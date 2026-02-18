package org.folio.settings.server.service;

import static org.folio.settings.server.TestUtils.postTenant;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;

import org.folio.okapi.common.XOkapiHeaders;
import org.folio.settings.server.TestContainersSupport;
import org.folio.settings.server.main.MainVerticle;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

class TenantAddressesServiceTest implements TestContainersSupport {

  private static final String TEST_USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String ISO_DATETIME_PATTERN =
      "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}\\.\\d{2,6}\\+00:00";
  private static final String TENANT = "diku";

  public static final String ADDRESS_CONFIGS = """
      {
        "configs": [
          {
            "id": "1ea38c7c-2622-40ba-9184-993b8d54a61d",
            "value": "{\\"name\\":\\"address1\\",\\"address\\":\\"address1-full\\"}"
          },
          {
            "id": "a0b2e65f-1c90-4f95-98c0-0208b7be8b61",
            "value": "{\\"name\\":\\"address2\\",\\"address\\":\\"address2-full\\"}"
          }
        ]
      }
      """;

  @BeforeAll
  static void beforeAll(Vertx vertx, VertxTestContext vtc) {
    RestAssured.baseURI = "http://localhost:8081";
    vertx.deployVerticle(new MainVerticle())
        .compose(x -> deployModConfigurationMock(vertx))
        .compose(x -> postTenant(vertx, "http://localhost:8081", TENANT, "1.3.0"))
        .onComplete(vtc.succeedingThenComplete());
  }

  @AfterAll
  static void afterAll(Vertx vertx, VertxTestContext vtc) {
    vertx.close()
        .onComplete(vtc.succeedingThenComplete());
  }

  private static Future<Void> deployModConfigurationMock(Vertx vertx) {
    return vertx.createHttpServer(new HttpServerOptions().setPort(8083))
        .requestHandler(req -> req.response().setStatusCode(200).send(ADDRESS_CONFIGS))
        .listen()
        .mapEmpty();
  }

  @Test
  void migration() {
    postTenant("http://localhost:8083", "migration", "1.3.0");

    assertThat(getTotalRecords("migration"), is(2));
  }

  @Test
  void getAddressWithoutMetadata(Vertx vertx, VertxTestContext vtc) {
    var addressId = UUID.randomUUID().toString();
    var addressName = uniqueName("no-metadata");

    // Insert address directly into database without metadata fields
    var pool = org.folio.tlib.postgres.TenantPgPool.pool(vertx, TENANT);
    pool.preparedQuery("INSERT INTO " + pool.getSchema() + ".tenant_addresses (id, name, address) VALUES ($1, $2, $3)")
        .execute(io.vertx.sqlclient.Tuple.of(UUID.fromString(addressId), addressName, "test-address"))
        .onComplete(vtc.succeeding(x -> {
          // Verify GET response excludes metadata field
          RestAssured.given()
              .header(XOkapiHeaders.TENANT, TENANT)
              .get("/tenant-addresses/" + addressId)
              .then()
              .statusCode(200)
              .body("metadata", nullValue());
          vtc.completeNow();
        }));
  }

  @Test
  void createTenantAddress() {
    var name = uniqueName("address");
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .header(XOkapiHeaders.USER_ID, TEST_USER_ID)
        .header("Content-Type", "application/json")
        .body(JsonObject.of("name", name, "address", "address1-full").encode())
        .post("/tenant-addresses")
        .then()
        .statusCode(201)
        .body("name", is(name))
        .body("address", is("address1-full"))
        .body("metadata", notNullValue())
        .body("metadata.updatedByUserId", is(TEST_USER_ID))
        .body("metadata.updatedDate", matchesPattern(ISO_DATETIME_PATTERN));
  }

  @Test
  void createMissingAddressFields() {
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .header("Content-Type", "application/json")
        .body(JsonObject.of("name", " ", "address", "address1-full").encode())
        .post("/tenant-addresses")
        .then()
        .statusCode(400)
        .body(containsString("missing"));
  }

  @Test
  void createDuplicateNameConflict() {
    var name = uniqueName("address-unique");
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .header("Content-Type", "application/json")
        .body(JsonObject.of("name", name, "address", "address-unique-full").encode())
        .post("/tenant-addresses")
        .then()
        .statusCode(201);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .header("Content-Type", "application/json")
        .body(JsonObject.of("name", name, "address", "address-unique-full2").encode())
        .post("/tenant-addresses")
        .then()
        .statusCode(422)
        .body(containsString("name already exists"));
  }

  @Test
  void getTenantAddresses() {
    var baseTotal = getTotalRecords(TENANT);
    var name1 = uniqueName("address");
    var name2 = uniqueName("address");
    createAddress(name1, "address1-full");
    createAddress(name2, "address2-full");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?limit=1&offset=0")
        .then()
        .statusCode(200)
        .body("addresses.size()", is(1));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?limit=1&offset=1")
        .then()
        .statusCode(200)
        .body("addresses.size()", is(1));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses")
        .then()
        .statusCode(200)
        .body("addresses.size()", is(baseTotal + 2))
        .body("addresses.name", hasItems(name1, name2));
  }

  @Test
  void getTenantAddressById() {
    var name = uniqueName("address");
    var createdId = createAddress(name, "address1-full");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses/" + createdId)
        .then()
        .statusCode(200)
        .body("name", is(name))
        .body("address", is("address1-full"))
        .body("metadata", notNullValue())
        .body("metadata.updatedByUserId", is(TEST_USER_ID))
        .body("metadata.updatedDate", matchesPattern(ISO_DATETIME_PATTERN));
  }

  @Test
  void updateTenantAddressById() {
    var createdId = createAddress(uniqueName("address"), "address1-full");
    var newName = uniqueName("address");
    var newAddress = "address1-full-updated";

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .header(XOkapiHeaders.USER_ID, TEST_USER_ID)
        .header("Content-Type", "application/json")
        .body(JsonObject.of("name", newName, "address", newAddress).encode())
        .put("/tenant-addresses/" + createdId)
        .then()
        .statusCode(204);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses/" + createdId)
        .then()
        .statusCode(200)
        .body("name", is(newName))
        .body("address", is(newAddress))
        .body("metadata", notNullValue())
        .body("metadata.updatedByUserId", is(TEST_USER_ID))
        .body("metadata.updatedDate", matchesPattern(ISO_DATETIME_PATTERN));
  }

  @Test
  void deleteTenantAddressById() {
    var createdId = createAddress(uniqueName("address"), "address1-full");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .delete("/tenant-addresses/" + createdId)
        .then()
        .statusCode(204);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses/" + createdId)
        .then()
        .statusCode(404);
  }

  private String createAddress(String name, String address) {
    return RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .header(XOkapiHeaders.USER_ID, TEST_USER_ID)
        .header("Content-Type", "application/json")
        .body(JsonObject.of("name", name, "address", address).encode())
        .post("/tenant-addresses")
        .then()
        .statusCode(201)
        .extract().path("id");
  }

  private int getTotalRecords(String tenant) {
    var response = RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
        .get("/tenant-addresses")
        .then()
        .statusCode(200)
        .extract();
    List<?> addresses = response.path("addresses");
    return addresses == null ? 0 : addresses.size();
  }

  private String uniqueName(String prefix) {
    return prefix + "-" + UUID.randomUUID();
  }
}
