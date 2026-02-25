package org.folio.settings.server.service;

import static org.folio.settings.server.TestUtils.postTenant;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.notNullValue;

import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.junit5.VertxTestContext;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.settings.server.TestContainersSupport;
import org.folio.settings.server.main.MainVerticle;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class TenantAddressesServiceTest implements TestContainersSupport {

  private static final String TEST_USER_ID = "11111111-1111-1111-1111-111111111111";
  private static final String ISO_DATETIME_PATTERN =
      "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{2,6})?\\+00:00";
  private static final String TENANT = "diku";

  public static final String ADDRESS_CONFIGS = """
      {
        "configs": [
          {
            "id": "1ea38c7c-2622-40ba-9184-993b8d54a61d",
            "value": "{\\"name\\":\\"address1\\",\\"address\\":\\"address1-full\\"}",
            "metadata": {
              "createdByUserId": "11111111-1111-1111-1111-111111111111",
              "createdDate": "2024-01-01T10:00:00.000+00:00",
              "updatedByUserId": "11111111-1111-1111-1111-111111111111",
              "updatedDate": "2024-01-01T10:00:00.000+00:00"
            }
          },
          {
            "id": "a0b2e65f-1c90-4f95-98c0-0208b7be8b61",
            "value": "{\\"name\\":\\"address2\\",\\"address\\":\\"address2-full\\"}",
            "metadata": {
              "createdByUserId": "22222222-2222-2222-2222-222222222222",
              "createdDate": "2024-01-02T10:00:00.000+00:00",
              "updatedByUserId": "22222222-2222-2222-2222-222222222222",
              "updatedDate": "2024-01-02T10:00:00.000+00:00"
            }
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

    // Verify migrated addresses have metadata
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, "migration")
        .get("/tenant-addresses")
        .then()
        .statusCode(200)
        .body("addresses[0].metadata", notNullValue())
        .body("addresses[0].metadata.createdByUserId", notNullValue())
        .body("addresses[0].metadata.createdDate", matchesPattern(ISO_DATETIME_PATTERN))
        .body("addresses[0].metadata.updatedByUserId", notNullValue())
        .body("addresses[0].metadata.updatedDate", matchesPattern(ISO_DATETIME_PATTERN));
  }


  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "address1-full")
  void createTenantAddress(String address) {
    var name = uniqueName("address");
    var body = new JsonObject().put("name", name);
    if (address != null) {
      body.put("address", address);
    }
    var spec = RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .header(XOkapiHeaders.USER_ID, TEST_USER_ID)
        .header("Content-Type", "application/json")
        .body(body.encode())
        .post("/tenant-addresses")
        .then()
        .statusCode(201)
        .body("name", is(name))
        .body("metadata", notNullValue())
        .body("metadata.createdByUserId", is(TEST_USER_ID))
        .body("metadata.createdDate", matchesPattern(ISO_DATETIME_PATTERN))
        .body("metadata.updatedByUserId", is(TEST_USER_ID))
        .body("metadata.updatedDate", matchesPattern(ISO_DATETIME_PATTERN));
    if (address != null) {
      spec.body("address", is(address));
    }
  }

  @Test
  void createTenantAddressIgnoresIncomingMetadata() {
    var name = uniqueName("address");
    var differentUserId = "99999999-9999-9999-9999-999999999999";
    var oldDate = "2020-01-01T10:00:00.000000+00:00";

    var requestBody = JsonObject.of(
        "name", name,
        "address", "address1-full",
        "metadata", JsonObject.of(
            "createdByUserId", differentUserId,
            "createdDate", oldDate,
            "updatedByUserId", differentUserId,
            "updatedDate", oldDate
        )
    ).encode();

    var response = RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .header(XOkapiHeaders.USER_ID, TEST_USER_ID)
        .header("Content-Type", "application/json")
        .body(requestBody)
        .post("/tenant-addresses")
        .then()
        .statusCode(201)
        .body("name", is(name))
        .body("address", is("address1-full"))
        .body("metadata", notNullValue())
        .body("metadata.createdByUserId", is(TEST_USER_ID))
        .body("metadata.createdDate", matchesPattern(ISO_DATETIME_PATTERN))
        .body("metadata.updatedByUserId", is(TEST_USER_ID))
        .body("metadata.updatedDate", matchesPattern(ISO_DATETIME_PATTERN))
        .extract();

    // Verify the metadata was regenerated and doesn't match the incoming metadata
    assertThat(response.path("metadata.createdByUserId"), is(TEST_USER_ID));
    assertThat(response.path("metadata.createdDate").toString(), is(notNullValue()));
    assertThat(response.path("metadata.createdDate").toString().contains("2020"), is(false));
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
    createAddress(name1, "addr1-full");
    createAddress(name2, "addr2-full");

    // paginate: offset=0, limit=2 returns exactly 2
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?offset=0&limit=2")
        .then()
        .statusCode(200)
        .body("addresses.size()", is(2));

    // paginate: offset=0, limit=1 returns exactly 1
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?limit=1&offset=0")
        .then()
        .statusCode(200)
        .body("addresses.size()", is(1));

    // paginate: offset=1, limit=1 returns exactly 1
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?limit=1&offset=1")
        .then()
        .statusCode(200)
        .body("addresses.size()", is(1));

    // no params – all records returned, both names present
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses")
        .then()
        .statusCode(200)
        .body("addresses.size()", is(baseTotal + 2))
        .body("addresses.name", hasItems(name1, name2));

    // cql.allRecords=1 behaves like no filter – both names still present
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?query=cql.allRecords=1")
        .then()
        .statusCode(200)
        .body("addresses.size()", is(baseTotal + 2))
        .body("addresses.name", hasItems(name1, name2));
  }

  // --- filter tests --------------------------------------------------------

  static Stream<String> filterQueries() {
    return Stream.of(
        "name==%s",           // exact match
        "(name==%s)"          // wrapped in parens
    );
  }

  @ParameterizedTest
  @MethodSource("filterQueries")
  void getTenantAddressesFilterByName(String queryTemplate) {
    var name = uniqueName("address");
    createAddress(name, "addr-filter-full");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?query=" + queryTemplate.formatted(name))
        .then()
        .statusCode(200)
        .body("addresses.size()", is(1))
        .body("addresses[0].name", is(name));
  }

  @Test
  void getTenantAddressesFilterById() {
    var name = uniqueName("address");
    var createdId = createAddress(name, "addr-filter-by-id");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?query=id==" + createdId)
        .then()
        .statusCode(200)
        .body("addresses.size()", is(1))
        .body("addresses[0].id", is(createdId))
        .body("addresses[0].name", is(name));
  }

  @Test
  void getTenantAddressesFilterByAddress() {
    var name = uniqueName("address");
    var addr = "unique-addr-value-" + UUID.randomUUID();
    createAddress(name, addr);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?query=address==" + addr)
        .then()
        .statusCode(200)
        .body("addresses.size()", is(1))
        .body("addresses[0].name", is(name));
  }

  @Test
  void getTenantAddressesFilterByMetadataCreatedByUserId() {
    var name = uniqueName("address");
    createAddress(name, "addr-meta-full");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?query=createdbyuserid==" + TEST_USER_ID)
        .then()
        .statusCode(200)
        .body("addresses.name", hasItems(name));
  }

  @Test
  void getTenantAddressesFilterByMetadataUpdatedByUserId() {
    var name = uniqueName("address");
    createAddress(name, "addr-meta-full");

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?query=updatedbyuserid==" + TEST_USER_ID)
        .then()
        .statusCode(200)
        .body("addresses.name", hasItems(name));
  }

  @Test
  void getTenantAddressesFilterNoMatch() {
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?query=name==no-such-name-" + UUID.randomUUID())
        .then()
        .statusCode(200)
        .body("addresses.size()", is(0));
  }

  // --- sort tests ----------------------------------------------------------

  static Stream<Arguments> sortQueries() {
    return Stream.of(
        Arguments.of("id", "sort.ascending"),
        Arguments.of("id", "sort.descending"),
        Arguments.of("name", "sort.ascending"),
        Arguments.of("name", "sort.descending"),
        Arguments.of("address", "sort.ascending"),
        Arguments.of("address", "sort.descending"),
        Arguments.of("createdbyuserid", "sort.ascending"),
        Arguments.of("createdbyuserid", "sort.descending"),
        Arguments.of("updatedbyuserid", "sort.ascending"),
        Arguments.of("updatedbyuserid", "sort.descending"),
        Arguments.of("createddate", "sort.ascending"),
        Arguments.of("createddate", "sort.descending"),
        Arguments.of("updateddate", "sort.ascending"),
        Arguments.of("updateddate", "sort.descending")
    );
  }

  @ParameterizedTest(name = "sortby {0}/{1}")
  @MethodSource("sortQueries")
  void getTenantAddressesSortBy(String field, String direction) {
    var nameA = uniqueName("sort-addr");
    var nameB = uniqueName("sort-addr");
    createAddress(nameA, "sort-full-a");
    createAddress(nameB, "sort-full-b");

    var query = "cql.allRecords=1 sortby " + field + "/" + direction;
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses?query=" + query)
        .then()
        .statusCode(200)
        .body("addresses.size()", is(Matchers.greaterThanOrEqualTo(2)));
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
        .body("metadata.createdByUserId", is(TEST_USER_ID))
        .body("metadata.createdDate", matchesPattern(ISO_DATETIME_PATTERN))
        .body("metadata.updatedByUserId", is(TEST_USER_ID))
        .body("metadata.updatedDate", matchesPattern(ISO_DATETIME_PATTERN));
  }

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = "address1-full-updated")
  void updateTenantAddressById(String newAddress) {
    var createdId = createAddress(uniqueName("address"), "address1-full");
    var newName = uniqueName("address");
    var body = new JsonObject().put("name", newName);
    if (newAddress != null) {
      body.put("address", newAddress);
    }

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .header(XOkapiHeaders.USER_ID, TEST_USER_ID)
        .header("Content-Type", "application/json")
        .body(body.encode())
        .put("/tenant-addresses/" + createdId)
        .then()
        .statusCode(204);

    var spec = RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses/" + createdId)
        .then()
        .statusCode(200)
        .body("name", is(newName))
        .body("metadata", notNullValue())
        .body("metadata.createdByUserId", is(TEST_USER_ID))
        .body("metadata.createdDate", matchesPattern(ISO_DATETIME_PATTERN))
        .body("metadata.updatedByUserId", is(TEST_USER_ID))
        .body("metadata.updatedDate", matchesPattern(ISO_DATETIME_PATTERN));
    if (newAddress != null) {
      spec.body("address", is(newAddress));
    }
  }

  @Test
  void updateTenantAddressIgnoresIncomingMetadata() {
    var createdId = createAddress(uniqueName("address"), "address1-full");

    // Get the original created date
    var originalCreatedDate = RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses/" + createdId)
        .then()
        .statusCode(200)
        .extract().path("metadata.createdDate").toString();

    var newName = uniqueName("address");
    var newAddress = "address1-full-updated";
    var differentUserId = "99999999-9999-9999-9999-999999999999";
    var oldDate = "2020-01-01T10:00:00.000000+00:00";

    var requestBody = JsonObject.of(
        "name", newName,
        "address", newAddress,
        "metadata", JsonObject.of(
            "createdByUserId", differentUserId,
            "createdDate", oldDate,
            "updatedByUserId", differentUserId,
            "updatedDate", oldDate
        )
    ).encode();

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .header(XOkapiHeaders.USER_ID, TEST_USER_ID)
        .header("Content-Type", "application/json")
        .body(requestBody)
        .put("/tenant-addresses/" + createdId)
        .then()
        .statusCode(204);

    var response = RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT)
        .get("/tenant-addresses/" + createdId)
        .then()
        .statusCode(200)
        .body("name", is(newName))
        .body("address", is(newAddress))
        .body("metadata", notNullValue())
        .body("metadata.updatedByUserId", is(TEST_USER_ID))
        .body("metadata.updatedDate", matchesPattern(ISO_DATETIME_PATTERN))
        .extract();

    // Verify the metadata was regenerated and doesn't match the incoming metadata
    assertThat(response.path("metadata.updatedByUserId"), is(TEST_USER_ID));
    assertThat(response.path("metadata.updatedDate").toString(), is(notNullValue()));
    assertThat(response.path("metadata.updatedDate").toString().contains("2020"), is(false));

    // Verify createdDate wasn't changed (should be null in update metadata but preserved in DB)
    assertThat(response.path("metadata.createdDate").toString(), is(originalCreatedDate));
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
