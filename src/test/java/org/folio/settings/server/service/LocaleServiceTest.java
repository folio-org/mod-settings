package org.folio.settings.server.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.restassured.RestAssured;
import io.restassured.response.ValidatableResponse;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.junit5.VertxExtension;
import io.vertx.junit5.VertxTestContext;
import io.vertx.sqlclient.Tuple;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.settings.server.main.MainVerticle;
import org.folio.tlib.postgres.TenantPgPool;
import org.folio.tlib.postgres.testing.TenantPgPoolContainer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@ExtendWith(VertxExtension.class)
@Testcontainers
class LocaleServiceTest {

  @Container
  public static PostgreSQLContainer<?> postgresContainer = TenantPgPoolContainer.create();

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext vtc) {
    RestAssured.baseURI = "http://localhost:8081";
    vertx.deployVerticle(new MainVerticle())
    .compose(x -> postTenant(vertx, "http://localhost:8081", "diku", "1.3.0"))
    .onComplete(vtc.succeedingThenComplete());
  }

  static Future<Void> postTenant(Vertx vertx, String okapiUrl, String tenant, String moduleTo) {
    var webClient = WebClient.create(vertx);
    var body = JsonObject.of("module_to", moduleTo);
    return webClient
        .postAbs("http://localhost:8081/_/tenant")
        .putHeader("X-Okapi-Url", okapiUrl)
        .putHeader("X-Okapi-Tenant", tenant)
        .putHeader("Content-Type", "application/json")
        .sendJsonObject(body)
        .compose(response -> {
          assertThat(response.statusCode(), is(201));
          var id = response.bodyAsJsonObject().getString("id");
          return webClient.getAbs("http://localhost:8081/_/tenant/" + id + "?wait=30000")
              .putHeader("X-Okapi-Tenant", tenant)
              .send();
        })
        .map(response -> {
          assertThat(response.statusCode(), is(200));
          assertThat(response.bodyAsJsonObject().getBoolean("complete"), is(true));
          return null;
        });
  }

  @Test
  void putAndGet() {
    RestAssured.given()
    .header(XOkapiHeaders.TENANT, "diku")
    .header("Content-Type", "application/json")
    .body(getDe().encode())
    .put("/locale")
    .then()
    .statusCode(201);

    RestAssured.given()
    .header(XOkapiHeaders.TENANT, "diku")
    .get("/locale")
    .then()
    .statusCode(200)
    .body("locale", is("de-DE"))
    .body("currency", is("EUR"))
    .body("timezone", is("Europe/Berlin"))
    .body("numberingSystem", is("arab"));
  }

  @ParameterizedTest
  @CsvSource({
    "locale, ''",
    "currency, ' '",
    "timezone, '  '",
    "numberingSystem,",
  })
  void blank(String key, String value) {
    RestAssured.given()
    .header(XOkapiHeaders.TENANT, "diku")
    .header("Content-Type", "application/json")
    .body(getDe().put(key, value).encode())
    .put("/locale")
    .then()
    .statusCode(400)
    .body(containsString(key));
  }

  @ParameterizedTest
  @ValueSource(strings={"latn", "arab"})
  void numberingSystem(String value) {
    RestAssured.given()
    .header(XOkapiHeaders.TENANT, "diku")
    .header("Content-Type", "application/json")
    .body(getDe().put("numberingSystem", value).encode())
    .put("/locale")
    .then()
    .statusCode(201);

    RestAssured.given()
    .header(XOkapiHeaders.TENANT, "diku")
    .get("/locale")
    .then()
    .statusCode(200)
    .body("numberingSystem", is(value));
  }

  @ParameterizedTest
  @ValueSource(strings={"LATN", "latin", " latn"})
  void illegalNumberingSystem(String value) {
    RestAssured.given()
    .header(XOkapiHeaders.TENANT, "diku")
    .header("Content-Type", "application/json")
    .body(getDe().put("numberingSystem", value).encode())
    .put("/locale")
    .then()
    .statusCode(400)
    .body(containsString("numberingSystem"));
  }

  @Test
  void wrongTenant() {
    RestAssured.given()
    .header(XOkapiHeaders.TENANT, "invalid")
    .header("Content-Type", "application/json")
    .body(getDe().encode())
    .put("/locale")
    .then()
    .statusCode(500);
  }

  @Test
  void blockDelete(Vertx vertx, VertxTestContext vtc) {
    var pool = TenantPgPool.pool(vertx, "diku");
    pool.execute("DELETE FROM " + pool.getSchema() + ".locale", Tuple.tuple())
    .onComplete(vtc.failing(e -> {
      assertThat(e.getMessage(), containsString("Cannot delete locale settings record."));
      vtc.completeNow();
    }));
  }

  @Test
  void blockInsert(Vertx vertx, VertxTestContext vtc) {
    var pool = TenantPgPool.pool(vertx, "diku");
    pool.execute("INSERT INTO " + pool.getSchema() + ".locale VALUES ($1, $2, $3, $4, $5)",
        Tuple.of("69395a75-a874-4299-9449-d69d39687fd0", "a", "b", "c", "d"))
    .onComplete(vtc.failing(e -> {
      assertThat(e.getMessage(), containsString("Cannot insert second locale settings record."));
      vtc.completeNow();
    }));
  }

  private JsonObject getDe() {
    return JsonObject.of(
        "locale", "de-DE",
        "currency", "EUR",
        "timezone", "Europe/Berlin",
        "numberingSystem", "arab");
  }

  Future<ValidatableResponse> postTenant(
      String tenant, String moduleTo, Vertx vertx, JsonObject config) {

    var configs = JsonObject.of("configs", JsonArray.of(config)).encodePrettily();
    return vertx.createHttpServer()
        .requestHandler(req -> req.response().setStatusCode(200).send(configs))
        .listen(0)
        .compose(srv -> postTenant(vertx, uri(srv), tenant, moduleTo))
        .map(x -> RestAssured.given()
            .header(XOkapiHeaders.TENANT, tenant)
            .get("/locale")
            .then()
            .statusCode(200));
  }

  String uri(HttpServer httpServer) {
    return "http://localhost:" + httpServer.actualPort();
  }

  @ParameterizedTest
  @CsvSource({
    // do 2nd migration
    "migration1, 1.2.0,  es-ES",
    // skip 2nd migration
    "migration2, 1.3.0,  ja",
    "migration3, 1.11.4, ja",
  })
  void migration(String tenant, String version, String expectedLocale,
      Vertx vertx, VertxTestContext vtc) {

    var config = JsonObject.of("locale", "es-ES");
    postTenant(tenant, version, vertx, config)
    .onSuccess(res -> {
      res.body("locale", is("es-ES"));

      RestAssured.given()
      .header(XOkapiHeaders.TENANT, tenant)
      .header("Content-Type", "application/json")
      .body(getDe().put("locale", "ja").encode())
      .put("/locale")
      .then()
      .statusCode(201);
    })
    .compose(x -> postTenant(tenant, version, vertx, config))
    .onComplete(vtc.succeeding(res -> {
      res.body("locale", is(expectedLocale));
      vtc.completeNow();
    }));
  }
}
