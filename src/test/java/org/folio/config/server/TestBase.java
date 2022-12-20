package org.folio.config.server;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.config.HttpClientConfig;
import io.restassured.config.RestAssuredConfig;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.web.client.WebClient;
import io.vertx.ext.web.client.predicate.ResponsePredicate;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.server.main.MainVerticle;
import org.folio.tlib.postgres.testing.TenantPgPoolContainer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.testcontainers.containers.PostgreSQLContainer;
import org.xml.sax.SAXException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class TestBase {
  protected final static Logger log = LogManager.getLogger("TestBase");
  protected static Vertx vertx;
  protected static WebClient webClient;
  protected static final int OKAPI_PORT = 9230;
  protected static final String OKAPI_URL = "http://localhost:" + OKAPI_PORT;
  protected static final int MODULE_PORT = 9231;
  protected static final String MODULE_URL = "http://localhost:" + MODULE_PORT;
  protected static final String TENANT_1 = "tenant1";
  protected static final String TENANT_2 = "tenant2";
  protected static final String MODULE_PREFIX = "mod-config";
  protected static final String MODULE_VERSION = "1.0.0";
  protected static final String MODULE_ID = MODULE_PREFIX + "-" + MODULE_VERSION;

  public static PostgreSQLContainer<?> postgresSQLContainer;

  @AfterClass
  public static void afterClass(TestContext context) {
    postgresSQLContainer.close();
    Future.succeededFuture()
        .compose(x -> vertx.close())
        .onComplete(x -> log.info("vertx close completed"))
        .onComplete(context.asyncAssertSuccess());
  }

  @BeforeClass
  public static void beforeClass(TestContext context) throws IOException, SAXException {
    postgresSQLContainer = TenantPgPoolContainer.create();

    vertx = Vertx.vertx();
    webClient = WebClient.create(vertx);

    RestAssured.config = RestAssuredConfig.config()
        .httpClient(HttpClientConfig.httpClientConfig()
            .setParam("http.socket.timeout", 15000)
            .setParam("http.connection.timeout", 5000));

    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = OKAPI_URL;
    RestAssured.requestSpecification = new RequestSpecBuilder().build();

    Future<Void> f = Future.succeededFuture();

    // deploy this module
    f = f.compose(e -> {
      DeploymentOptions deploymentOptions = new DeploymentOptions();
      deploymentOptions.setConfig(new JsonObject().put("port", Integer.toString(MODULE_PORT)));
      return vertx.deployVerticle(new MainVerticle(), deploymentOptions)
          .mapEmpty();
    });

    // deploy okapi
    DeploymentOptions okapiOptions = new DeploymentOptions();
    okapiOptions.setConfig(new JsonObject()
        .put("port", Integer.toString(OKAPI_PORT))
        .put("mode", "proxy")
    );
    f = f.compose(e -> vertx.deployVerticle(new org.folio.okapi.MainVerticle(), okapiOptions))
        .mapEmpty();

    String md = Files.readString(Path.of("descriptors/ModuleDescriptor-template.json"))
        .replace("${artifactId}", MODULE_PREFIX)
        .replace("${version}", MODULE_VERSION);
    log.info("Module .. {}", md);

    // register module
    f = f.compose(t ->
        webClient.postAbs(OKAPI_URL + "/_/proxy/modules")
            .expect(ResponsePredicate.SC_CREATED)
            .sendJsonObject(new JsonObject(md))
            .mapEmpty());

    // tell okapi where our module is running
    f = f.compose(t ->
        webClient.postAbs(OKAPI_URL + "/_/discovery/modules")
            .expect(ResponsePredicate.SC_CREATED)
            .sendJsonObject(new JsonObject()
                .put("instId", MODULE_ID)
                .put("srvcId", MODULE_ID)
                .put("url", MODULE_URL))
            .mapEmpty());

    // create tenant 1
    f = f.compose(t ->
        webClient.postAbs(OKAPI_URL + "/_/proxy/tenants")
            .expect(ResponsePredicate.SC_CREATED)
            .sendJsonObject(new JsonObject().put("id", TENANT_1))
            .mapEmpty());

    // enable module for tenant 1
    f = f.compose(e ->
        webClient.postAbs(OKAPI_URL + "/_/proxy/tenants/" + TENANT_1 + "/install")
            .expect(ResponsePredicate.SC_OK)
            .sendJson(new JsonArray().add(new JsonObject()
                .put("id", MODULE_PREFIX)
                .put("action", "enable")))
            .mapEmpty());

    // create tenant 2
    f = f.compose(t ->
        webClient.postAbs(OKAPI_URL + "/_/proxy/tenants")
            .expect(ResponsePredicate.SC_CREATED)
            .sendJsonObject(new JsonObject().put("id", TENANT_2))
            .mapEmpty());

    // enable module for tenant 2
    f = f.compose(e ->
        webClient.postAbs(OKAPI_URL + "/_/proxy/tenants/" + TENANT_2 + "/install")
            .expect(ResponsePredicate.SC_OK)
            .sendJson(new JsonArray().add(new JsonObject()
                .put("id", MODULE_PREFIX)
                .put("action", "enable")))
            .mapEmpty());

    f.onComplete(context.asyncAssertSuccess());
  }

}
