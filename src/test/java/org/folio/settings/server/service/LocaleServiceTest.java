package org.folio.settings.server.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
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
    .compose(x -> deployModConfigurationMock(vertx))
    .compose(x -> postTenant(vertx, "http://localhost:8081", "diku", "1.3.0"))
    .onComplete(vtc.succeedingThenComplete());
  }

  private static Future<Void> deployModConfigurationMock(Vertx vertx) {
    var configs = """
                  {
                    "configs": [
                      {
                        "locale": "es-ES"
                      }
                    ]
                  }
                  """;
    return vertx.createHttpServer(new HttpServerOptions().setPort(8082))
        .requestHandler(req -> req.response().setStatusCode(200).send(configs))
        .listen()
        .mapEmpty();
  }

  private static Future<Void> postTenant(Vertx vertx, String okapiUrl, String tenant, String moduleTo) {
    return vertx.executeBlocking(() -> {
      postTenant(okapiUrl, tenant, moduleTo);
      return null;
    });
  }

  private static void postTenant(String okapiUrl, String tenant, String moduleTo) {
    var body = JsonObject.of("module_to", moduleTo).encodePrettily();
    var id = RestAssured.given()
        .header("X-Okapi-Url", okapiUrl)
        .header("X-Okapi-Tenant", tenant)
        .contentType("application/json")
        .body(body)
        .post("/_/tenant")
        .then()
        .statusCode(201)
        .extract().path("id");

    RestAssured.given()
    .header("X-Okapi-Tenant", tenant)
    .get("/_/tenant/" + id + "?wait=30000")
    .then()
    .statusCode(200)
    .body("complete", is(true));
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

  @ParameterizedTest
  @CsvSource({
    // do 2nd migration
    "migration1, 1.2.0,  es-ES",
    // skip 2nd migration
    "migration2, 1.3.0,  ja",
    "migration3, 1.11.4, ja",
  })
  void migration(String tenant, String version, String expectedLocale) {
    postTenant("http://localhost:8082", tenant, version);
    assertThat(getLocale(tenant), is("es-ES"));

    RestAssured.given()
    .header(XOkapiHeaders.TENANT, tenant)
    .contentType("application/json")
    .body(getDe().put("locale", "ja").encode())
    .put("/locale")
    .then()
    .statusCode(201);

    postTenant("http://localhost:8082", tenant, version);
    assertThat(getLocale(tenant), is(expectedLocale));
  }

  private String getLocale(String tenant) {
    return RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .get("/locale")
        .then()
        .statusCode(200)
        .extract().path("locale");
  }
}
