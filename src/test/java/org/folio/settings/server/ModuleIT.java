package org.folio.settings.server;

import static io.restassured.RestAssured.given;
import static io.restassured.RestAssured.when;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.RestAssured;
import io.restassured.builder.RequestSpecBuilder;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.images.builder.ImageFromDockerfile;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.nginx.NginxContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Test that shaded fat uber jar and Dockerfile work.
 *
 * <p>Smoke tests: /admin/health and migration.
 */
@Testcontainers
class ModuleIT {

  private static final Logger LOG = LoggerFactory.getLogger(ModuleIT.class);
  private static final Network NETWORK = Network.newNetwork();
  private static final DockerImageName POSTGRES_IMAGE_NAME = DockerImageName.parse(
      Objects.toString(System.getenv("TESTCONTAINERS_POSTGRES_IMAGE"), "postgres:16-alpine"));

  @Container
  @SuppressWarnings("resource")
  static final NginxContainer OKAPI = new NginxContainer("nginx:stable-alpine-slim")
      .withNetwork(NETWORK)
      .withNetworkAliases("okapi")
      .withExposedPorts(8080)
      .withCopyToContainer(Transferable.of("""
            # by default returns 404 for all paths
            server {
              listen 8080;
            }
            """), "/etc/nginx/conf.d/default.conf");

  @Container
  @SuppressWarnings("resource")
  static final PostgreSQLContainer POSTGRES =
    new PostgreSQLContainer(POSTGRES_IMAGE_NAME)
    .withNetwork(NETWORK)
    .withNetworkAliases("postgres")
    .withExposedPorts(5432)
    .withUsername("username")
    .withPassword("password")
    .withDatabaseName("postgres");

  @Container
  @SuppressWarnings("resource")
  static final GenericContainer<?> MOD_SETTINGS =
    new GenericContainer<>(
      new ImageFromDockerfile("mod-settings").withFileFromPath(".", Path.of(".")))
    .dependsOn(OKAPI, POSTGRES)
    .withNetwork(NETWORK)
    .withExposedPorts(8081)
    .withEnv("DB_HOST", "postgres")
    .withEnv("DB_PORT", "5432")
    .withEnv("DB_USERNAME", "username")
    .withEnv("DB_PASSWORD", "password")
    .withEnv("DB_DATABASE", "postgres");

  @BeforeAll
  static void beforeClass() {
    MOD_SETTINGS.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams().withPrefix("mod-settings"));
    OKAPI.followOutput(new Slf4jLogConsumer(LOG).withSeparateOutputStreams().withPrefix("okapi"));
  }

  private void tenant(String tenant) {
    RestAssured.reset();
    RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    RestAssured.baseURI = "http://" + MOD_SETTINGS.getHost() + ":" + MOD_SETTINGS.getFirstMappedPort();
    System.out.println("qqq tenant(" + tenant + "), baseURI=" + RestAssured.baseURI);
    var headers = tenant == null
        ? Map.of("X-Okapi-Url", "http://okapi:8080")
        : Map.of("X-Okapi-Url", "http://okapi:8080", "X-Okapi-Tenant", tenant);
    RestAssured.requestSpecification = new RequestSpecBuilder()
        .addHeaders(headers)
        .setContentType(ContentType.JSON)
        .build();
  }

  @BeforeEach
  void beforeEach() {
    tenant(null);  // unset X-Okapi-Tenant header
  }

  @Test
  void health() {
    when().
      get("/admin/health").
    then().
      statusCode(200).
      body(is("OK"));
  }

  private void postTenant(JsonObject body) {
    String location =
        given().
          body(body.encodePrettily()).
        when().
          post("/_/tenant").
        then().
          statusCode(201).
        extract().
          header("Location");

    when().
      get(location + "?wait=30000").
    then().
      statusCode(200).  // getting job record succeeds
      body("complete", is(true)).  // job is complete
      body("error", is(nullValue()));  // job has succeeded without error
  }

  @Test
  void installAndUpgrade() {
    tenant("latest");
    postTenant(new JsonObject().put("module_to", "mod-settings-999999.0.0"));
    // migrate from 0.0.0, migration should be idempotent
    postTenant(new JsonObject().put("module_to", "mod-settings-999999.0.0")
        .put("module_from", "mod-settings-0.0.0"));

    given().
      body(new JsonObject().put("baseUrl", "https://bee.example.com").encode()).
    when().
      put("/base-url").
    then().
      statusCode(201);

    when().
      get("/base-url").
    then().
      statusCode(200).
      body("baseUrl", is("https://bee.example.com"));
  }

}
