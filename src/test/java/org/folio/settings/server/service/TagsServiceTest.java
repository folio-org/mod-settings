package org.folio.settings.server.service;

import static org.folio.settings.server.TestUtils.postTenant;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.settings.server.TestContainersSupport;
import org.folio.settings.server.main.MainVerticle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TagsServiceTest implements TestContainersSupport {

  private static final int MOCK_PORT = 8084;
  private static final String MOCK_URL = "http://localhost:" + MOCK_PORT;
  private static final String SCOPE_QUERY = "scope=\"ui-tags.tags.manage\" and key=tags_enabled";
  private static final ConcurrentHashMap<String, Integer> MOCK_STATUS = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, String> MOCK_VALUE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, String> MOCK_MODULE = new ConcurrentHashMap<>();
  private static final ConcurrentHashMap<String, String> MOCK_CONFIG_NAME = new ConcurrentHashMap<>();

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext vtc) {
    RestAssured.baseURI = "http://localhost:8081";
    vertx.deployVerticle(new MainVerticle())
        .compose(x -> deployModConfigurationMock(vertx))
        .onComplete(vtc.succeedingThenComplete());
  }

  @AfterAll
  static void afterAll(Vertx vertx, VertxTestContext vtc) {
    vertx.close()
        .onComplete(vtc.succeedingThenComplete());
  }

  private static Future<Void> deployModConfigurationMock(Vertx vertx) {
    return vertx.createHttpServer(new HttpServerOptions().setPort(MOCK_PORT))
        .requestHandler(req -> {
          String tenant = req.getHeader(XOkapiHeaders.TENANT);
          int status = MOCK_STATUS.getOrDefault(tenant, 200);
          if (status != 200) {
            req.response().setStatusCode(status).end();
            return;
          }
          String value = MOCK_VALUE.get(tenant);
          String body;
          if (value == null) {
            body = "{\"configs\":[]}";
          } else {
            String module = MOCK_MODULE.getOrDefault(tenant, "TAGS");
            String configName = MOCK_CONFIG_NAME.getOrDefault(tenant, "tags_enabled");
            body = """
                {"configs":[{"id":"%s","module":"%s","configName":"%s","value":"%s"}]}
                """.formatted(UUID.randomUUID(), module, configName, value);
          }
          req.response().setStatusCode(200).end(body);
        })
        .listen()
        .mapEmpty();
  }

  private static String postTenantExpectingResult(String tenant, String moduleTo) {
    var id = RestAssured.given()
        .header(XOkapiHeaders.URL, MOCK_URL)
        .header(XOkapiHeaders.TENANT, tenant)
        .contentType("application/json")
        .body(JsonObject.of("module_to", moduleTo).encodePrettily())
        .post("/_/tenant")
        .then()
        .statusCode(201)
        .extract().path("id");

    return RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .get("/_/tenant/" + id + "?wait=30000")
        .then()
        .statusCode(200)
        .body("complete", is(true))
        .extract().path("error");
  }

  @ParameterizedTest
  @ValueSource(strings = {"true", "false"})
  void migratesWhenAbsent(String value) {
    String tenant = "tagsmigrate" + value;
    MOCK_VALUE.put(tenant, value);

    postTenant(MOCK_URL, tenant, "1.4.0");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .header(XOkapiHeaders.PERMISSIONS,
            new JsonArray().add("mod-settings.global.read.ui-tags.tags.manage").encode())
        .queryParam("query", SCOPE_QUERY)
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].value", is(Boolean.parseBoolean(value)));
  }

  @Test
  void noConfigPresentSucceedsWithoutMigrating() {
    String tenant = "tagsempty";

    assertThat(postTenantExpectingResult(tenant, "1.4.0"), nullValue());
    assertNoEntry(tenant);
  }

  @Test
  void notFoundSucceedsWithoutMigrating() {
    String tenant = "tagsnotfound";
    MOCK_STATUS.put(tenant, 404);

    assertThat(postTenantExpectingResult(tenant, "1.4.0"), nullValue());
    assertNoEntry(tenant);
  }

  @Test
  void mismatchedConfigEntrySucceedsWithoutMigrating() {
    String tenant = "tagsmismatch";
    MOCK_VALUE.put(tenant, "true");
    MOCK_MODULE.put(tenant, "ORG");
    MOCK_CONFIG_NAME.put(tenant, "localeSettings");

    assertThat(postTenantExpectingResult(tenant, "1.4.0"), nullValue());
    assertNoEntry(tenant);
  }

  @Test
  void moduleMismatchOnlySucceedsWithoutMigrating() {
    String tenant = "tagsmodulemismatch";
    MOCK_VALUE.put(tenant, "true");
    MOCK_MODULE.put(tenant, "ORG");

    assertThat(postTenantExpectingResult(tenant, "1.4.0"), nullValue());
    assertNoEntry(tenant);
  }

  @Test
  void configNameMismatchOnlySucceedsWithoutMigrating() {
    String tenant = "tagsconfignamemismatch";
    MOCK_VALUE.put(tenant, "true");
    MOCK_CONFIG_NAME.put(tenant, "localeSettings");

    assertThat(postTenantExpectingResult(tenant, "1.4.0"), nullValue());
    assertNoEntry(tenant);
  }

  @Test
  void serverErrorFailsTheUpgrade() {
    String tenant = "tagsservererror";
    MOCK_STATUS.put(tenant, 500);

    assertThat(postTenantExpectingResult(tenant, "1.4.0"),
        containsString("Failed to migrate tags_enabled setting"));
    assertNoEntry(tenant);
  }

  @Test
  void malformedValueFailsTheUpgrade() {
    String tenant = "tagsbadvalue";
    MOCK_VALUE.put(tenant, "notaboolean");

    assertThat(postTenantExpectingResult(tenant, "1.4.0"),
        containsString("Failed to migrate tags_enabled setting"));
    assertNoEntry(tenant);
  }

  @Test
  void doesNotOverwriteExistingEntry() {
    String tenant = "tagsclobbercheck";

    // First call: a brand-new tenant's stored version is always 0.0.0, so the Tags gate
    // fires on this call regardless of the "1.0.0" requested here. The mock has no
    // value queued for this tenant yet, so it's a no-op — this bootstraps the tenant's
    // tables (including "settings") without creating a tags_enabled entry.
    postTenant(MOCK_URL, tenant, "1.0.0");

    var preExistingId = UUID.randomUUID().toString();
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .header(XOkapiHeaders.PERMISSIONS,
            new JsonArray().add("mod-settings.global.write.ui-tags.tags.manage").encode())
        .contentType("application/json")
        .body(JsonObject.of(
            "id", preExistingId,
            "scope", "ui-tags.tags.manage",
            "key", "tags_enabled",
            "value", false).encode())
        .post("/settings/entries")
        .then()
        .statusCode(204);

    // Second call: the tenant's stored version is still "1.0.0" (below 1.4.0), so the
    // gate fires again. The mock now returns true — the migration must not overwrite
    // the entry pre-created above.
    MOCK_VALUE.put(tenant, "true");
    postTenant(MOCK_URL, tenant, "1.4.0");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .header(XOkapiHeaders.PERMISSIONS,
            new JsonArray().add("mod-settings.global.read.ui-tags.tags.manage").encode())
        .queryParam("query", SCOPE_QUERY)
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .body("items", hasSize(1))
        .body("items[0].id", is(preExistingId))
        .body("items[0].value", is(false));
  }

  private void assertNoEntry(String tenant) {
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .header(XOkapiHeaders.PERMISSIONS,
            new JsonArray().add("mod-settings.global.read.ui-tags.tags.manage").encode())
        .queryParam("query", SCOPE_QUERY)
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .body("items", hasSize(0));
  }
}
