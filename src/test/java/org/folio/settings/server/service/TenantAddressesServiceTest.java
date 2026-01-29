package org.folio.settings.server.service;

import static org.folio.settings.server.TestUtils.postTenant;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;

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
        .compose(x -> postTenant(vertx, "http://localhost:8081", "diku", "1.3.0"))
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
  void createTenantAddress() {
    var name = uniqueName("address");
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, "diku")
        .header("Content-Type", "application/json")
        .body(JsonObject.of("name", name, "address", "address1-full").encode())
        .post("/tenant-addresses")
        .then()
        .statusCode(201)
        .body("name", is(name))
        .body("address", is("address1-full"));
  }

  @Test
  void createMissingAddressFields() {
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, "diku")
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
        .header(XOkapiHeaders.TENANT, "diku")
        .header("Content-Type", "application/json")
        .body(JsonObject.of("name", name, "address", "address-unique-full").encode())
        .post("/tenant-addresses")
        .then()
        .statusCode(201);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, "diku")
        .header("Content-Type", "application/json")
        .body(JsonObject.of("name", name, "address", "address-unique-full2").encode())
        .post("/tenant-addresses")
        .then()
        .statusCode(409)
        .body(containsString("name already exists"));
  }

  @Test
  void getTenantAddresses() {
    var baseTotal = getTotalRecords("diku");
    var name1 = uniqueName("address");
    var name2 = uniqueName("address");
    createAddress("diku", name1, "address1-full");
    createAddress("diku", name2, "address2-full");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, "diku")
        .get("/tenant-addresses?limit=1&offset=0")
        .then()
        .statusCode(200)
        .body("addresses.size()", is(1));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, "diku")
        .get("/tenant-addresses?limit=1&offset=1")
        .then()
        .statusCode(200)
        .body("addresses.size()", is(1));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, "diku")
        .get("/tenant-addresses")
        .then()
        .statusCode(200)
        .body("addresses.size()", is(baseTotal + 2))
        .body("addresses.name", hasItems(name1, name2));
  }

  @Test
  void getTenantAddressById() {
    var name = uniqueName("address");
    var createdId = createAddress("diku", name, "address1-full");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, "diku")
        .get("/tenant-addresses/" + createdId)
        .then()
        .statusCode(200)
        .body("name", is(name))
        .body("address", is("address1-full"));
  }

  @Test
  void updateTenantAddressById() {
    var createdId = createAddress("diku", uniqueName("address"), "address1-full");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, "diku")
        .header("Content-Type", "application/json")
        .body(JsonObject.of("name", uniqueName("address"), "address", "address1-full-updated").encode())
        .put("/tenant-addresses/" + createdId)
        .then()
        .statusCode(204);

    var updated = RestAssured.given()
        .header(XOkapiHeaders.TENANT, "diku")
        .get("/tenant-addresses/" + createdId)
        .then()
        .statusCode(200)
        .extract();
    var updatedName = updated.path("name");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, "diku")
        .get("/tenant-addresses/" + createdId)
        .then()
        .statusCode(200)
        .body("name", is(updatedName))
        .body("address", is("address1-full-updated"));
  }

  @Test
  void deleteTenantAddressById() {
    var createdId = createAddress("diku", uniqueName("address"), "address1-full");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, "diku")
        .delete("/tenant-addresses/" + createdId)
        .then()
        .statusCode(204);
  }

  private String createAddress(String tenant, String name, String address) {
    return RestAssured.given()
        .header(XOkapiHeaders.TENANT, tenant)
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
