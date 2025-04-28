package org.folio.settings.server;

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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.ChattyHttpResponseExpectation;
import org.folio.settings.server.main.MainVerticle;
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
  protected static final String MODULE_PREFIX = "mod-settings";
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

    // register mock auth
    JsonObject authDescriptor = new JsonObject()
        .put("id", "auth-1.0.0")
        .put("name", "Mock Auth")
        .put("provides", new JsonArray()
                .add(new JsonObject()
                    .put("id", "authtoken")
                    .put("version", "2.1")))
        .put("requires", new JsonArray());

    f = f.compose(t ->
        webClient.postAbs(OKAPI_URL + "/_/proxy/modules")
            .sendJsonObject(authDescriptor)
            .expecting(ChattyHttpResponseExpectation.SC_CREATED)
            .mapEmpty());

    // register module mod-settings
    String mdTemplate = Files.readString(Path.of("descriptors/ModuleDescriptor-template.json"));
    JsonObject moduleDescriptor = new JsonObject(mdTemplate);
    moduleDescriptor.put("id",
      moduleDescriptor.getString("id")
      .replace("${artifactId}", MODULE_PREFIX)
      .replace("${version}", MODULE_VERSION));

    f = f.compose(t ->
        webClient.postAbs(OKAPI_URL + "/_/proxy/modules")
            .sendJsonObject(moduleDescriptor)
            .expecting(ChattyHttpResponseExpectation.SC_CREATED)
            .mapEmpty());

    // tell okapi where our module is running
    f = f.compose(t ->
        webClient.postAbs(OKAPI_URL + "/_/discovery/modules")
            .sendJsonObject(new JsonObject()
                .put("instId", MODULE_ID)
                .put("srvcId", MODULE_ID)
                .put("url", MODULE_URL))
            .expecting(ChattyHttpResponseExpectation.SC_CREATED)
            .mapEmpty());

    // create tenant 1
    f = f.compose(t ->
        webClient.postAbs(OKAPI_URL + "/_/proxy/tenants")
            .sendJsonObject(new JsonObject().put("id", TENANT_1))
            .expecting(ChattyHttpResponseExpectation.SC_CREATED)
            .mapEmpty());

    // enable module for tenant 1
    f = f.compose(e ->
        webClient.postAbs(OKAPI_URL + "/_/proxy/tenants/" + TENANT_1 + "/install")
            .sendJson(new JsonArray().add(new JsonObject()
                .put("id", MODULE_PREFIX)
                .put("action", "enable")))
            .expecting(ChattyHttpResponseExpectation.SC_OK)
            .mapEmpty());

    // create tenant 2
    f = f.compose(t ->
        webClient.postAbs(OKAPI_URL + "/_/proxy/tenants")
            .sendJsonObject(new JsonObject().put("id", TENANT_2))
            .expecting(ChattyHttpResponseExpectation.SC_CREATED)
            .mapEmpty());

    // enable module for tenant 2
    f = f.compose(e ->
        webClient.postAbs(OKAPI_URL + "/_/proxy/tenants/" + TENANT_2 + "/install")
            .sendJson(new JsonArray().add(new JsonObject()
                .put("id", MODULE_PREFIX)
                .put("action", "enable")))
            .expecting(ChattyHttpResponseExpectation.SC_OK)
            .mapEmpty());

    f.onComplete(context.asyncAssertSuccess());
  }

}
