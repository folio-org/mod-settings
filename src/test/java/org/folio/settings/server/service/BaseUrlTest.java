package org.folio.settings.server.service;

import static org.folio.settings.server.TestUtils.postTenant;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Tuple;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.settings.server.TestContainersSupport;
import org.folio.settings.server.main.MainVerticle;
import org.folio.settings.server.storage.BaseUrlStorage;
import org.folio.tlib.postgres.TenantPgPool;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BaseUrlTest implements TestContainersSupport {

  private static final String FOLIO_HOST_CONFIGS = """
      {
        "configs": [
          {
            "id": "69206399-3002-49f9-8c28-f129f211e402",
            "module": "USERSBL",
            "configName": "FOLIO host",
            "code": "FOLIO_HOST",
            "description": "FOLIO host address for password reset",
            "default": true,
            "enabled": true,
            "value": "https://example.org"
          }
        ]
      }
      """;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext vtc) {
    RestAssured.baseURI = "http://localhost:8081";
    vertx.deployVerticle(new MainVerticle())
    .compose(x -> deployModConfigurationMock(vertx))
    .compose(x -> postTenant(vertx, "http://localhost:8081", "diku", "1.3.0"))
    .onComplete(vtc.succeedingThenComplete());
  }

  @AfterAll
  static void afterAll(Vertx vertx, VertxTestContext vtc) {
    vertx.close()
        .onComplete(vtc.succeedingThenComplete());
  }

  private static Future<Void> deployModConfigurationMock(Vertx vertx) {
    return vertx.createHttpServer(new HttpServerOptions().setPort(8082))
        .requestHandler(req -> req.response().setStatusCode(200).send(FOLIO_HOST_CONFIGS))
        .listen()
        .mapEmpty();
  }

  @Test
  void putAndGet() {
    RestAssured.given()
    .header(XOkapiHeaders.TENANT, "diku")
    .header("Content-Type", "application/json")
    .body(JsonObject.of("baseUrl", "https://foo.example.com").encode())
    .put("/base-url")
    .then()
    .statusCode(201);

    RestAssured.given()
    .header(XOkapiHeaders.TENANT, "diku")
    .get("/base-url")
    .then()
    .statusCode(200)
    .body("baseUrl", is("https://foo.example.com"));
  }

  @ParameterizedTest
  @CsvSource({
    ", missing",  // null
    "'', missing",  // empty
    "'  ', missing",
    "https://foo.example.com/, slash",
  })
  void invalidBaseUrl(String baseUrl, String error) {
    var json = baseUrl == null ? JsonObject.of() : JsonObject.of("baseUrl", baseUrl);
    RestAssured.given()
    .header(XOkapiHeaders.TENANT, "diku")
    .header("Content-Type", "application/json")
    .body(json.encode())
    .put("/base-url")
    .then()
    .statusCode(400)
    .body(containsString(error));
  }

  @Test
  void wrongTenant() {
    RestAssured.given()
    .header(XOkapiHeaders.TENANT, "invalid")
    .header("Content-Type", "application/json")
    .body(JsonObject.of("baseUrl", "https://foo.example.com").encode())
    .put("/base-url")
    .then()
    .statusCode(500);
  }

  @Test
  void blockDelete(Vertx vertx, VertxTestContext vtc) {
    var pool = TenantPgPool.pool(vertx, "diku");
    pool.execute("DELETE FROM " + table(pool), Tuple.tuple())
    .onComplete(vtc.failing(e -> {
      assertThat(e.getMessage(), containsString("Cannot delete baseurl record."));
      vtc.completeNow();
    }));
  }

  @Test
  void blockInsert(Vertx vertx, VertxTestContext vtc) {
    var pool = TenantPgPool.pool(vertx, "diku");
    pool.execute("INSERT INTO " + table(pool) + " VALUES ($1, $2)",
        Tuple.of("ca909fa2-afe2-4ae1-9338-d893e7c84b58", "https://localhost:8000"))
    .onComplete(vtc.failing(e -> {
      assertThat(e.getMessage(), containsString("Cannot insert second baseurl record."));
      vtc.completeNow();
    }));
  }

  @ParameterizedTest
  @CsvSource({
    // do 2nd migration
    "baseurl1, 1.2.0,  https://example.org",
    // skip 2nd migration
    "baseurl2, 1.3.0,  https://example.edu",
    "baseurl3, 1.11.4, https://example.edu",
  })
  void migration(String tenant, String version, String expectedBaseUrl) {
    postTenant("http://localhost:8082", tenant, version);
    assertThat(getBaseUrl(tenant), is("https://example.org"));

    RestAssured.given()
    .header(XOkapiHeaders.TENANT, tenant)
    .contentType("application/json")
    .body(JsonObject.of("baseUrl", "https://example.edu").encode())
    .put("/base-url")
    .then()
    .statusCode(201);

    postTenant("http://localhost:8082", tenant, version);
    assertThat(getBaseUrl(tenant), is(expectedBaseUrl));
  }

  private String getBaseUrl(String tenant) {
    return RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .get("/base-url")
        .then()
        .statusCode(200)
        .extract().path("baseUrl");
  }

  private static String table(TenantPgPool pool) {
    return pool.getSchema() + ".baseurl";
  }
}
