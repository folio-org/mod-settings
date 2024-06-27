package org.folio.settings.server.main;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.UUID;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.settings.server.TestBase;
import org.folio.settings.server.service.SettingsService;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest extends TestBase {
  @Test
  public void testAdminHealth() {
    RestAssured.given()
        .baseUri(MODULE_URL)
        .get("/admin/health")
        .then()
        .statusCode(200)
        .contentType(ContentType.TEXT);
  }

  @Test
  public void testCrudGlobalOk() {
    // values that we store and retrieve
    JsonArray ar = new JsonArray()
        .add("simple")
        .add(new JsonObject().put("key", "k1"))
        .add(new JsonArray().add("1").add("2"))
        .add(234);

    for (int i = 0; i < ar.size(); i++) {
      JsonObject en = new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("scope", UUID.randomUUID().toString())
          .put("key", "k1")
          .put("value", ar.getValue(i));
      JsonArray permRead = new JsonArray().add("mod-settings.global.read." + en.getString("scope"));
      JsonArray permWrite = new JsonArray().add("mod-settings.global.write." + en.getString("scope"));

      RestAssured.given()
          .header(XOkapiHeaders.TENANT, TENANT_1)
          .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
          .contentType(ContentType.JSON)
          .body(en.encode())
          .post("/settings/entries")
          .then()
          .statusCode(204);

      RestAssured.given()
          .header(XOkapiHeaders.TENANT, TENANT_1)
          .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
          .get("/settings/entries/" + en.getString("id"))
          .then()
          .statusCode(200)
          .contentType(ContentType.JSON)
          .body(is(en.encode()));

      RestAssured.given()
          .header(XOkapiHeaders.TENANT, TENANT_1)
          .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
          .delete("/settings/entries/" + en.getString("id"))
          .then()
          .statusCode(204);
    }
  }

  @Test
  public void testPostMissingTenant() {
    JsonObject en = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("value", new JsonObject().put("v", "thevalue"));
    JsonArray permWrite = new JsonArray().add("mod-settings.global.write." + en.getString("scope"));

    RestAssured.given()
        .baseUri(MODULE_URL)  // if not, Okapi will intercept
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en.encode())
        .post("/settings/entries")
        .then()
        .statusCode(400)
        .contentType(ContentType.TEXT)
        .body(containsString("Missing parameter X-Okapi-Tenant"));
  }

  @Test
  public void testMissingPermissionsHeader() {
    JsonObject en = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("value", new JsonObject().put("v", "thevalue"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .contentType(ContentType.JSON)
        .body(en.encode())
        .post("/settings/entries")
        .then()
        .statusCode(400)
        .contentType(ContentType.TEXT)
        .body(containsString("Missing parameter X-Okapi-Permissions in HEADER"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .contentType(ContentType.JSON)
        .get("/settings/entries")
        .then()
        .statusCode(400)
        .contentType(ContentType.TEXT)
        .body(containsString("Missing parameter X-Okapi-Permissions in HEADER"));
  }

  @Test
  public void testPostInvalidSetting() {
    JsonObject en = new JsonObject()
        // id missing
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("value", new JsonObject().put("v", "thevalue"));
    JsonArray permWrite = new JsonArray().add("mod-settings.global.write." + en.getString("scope"));

    RestAssured.given()
        .baseUri(MODULE_URL)  // if not, Okapi will intercept
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en.encode())
        .post("/settings/entries")
        .then()
        .statusCode(400)
        .contentType(ContentType.TEXT)
        .body(containsString("provided object should contain property id"));
  }

  @Test
  public void testPostBodyTooBig() {
    JsonObject en = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("value", new JsonObject().put("v", "x".repeat(SettingsService.BODY_LIMIT)));
    JsonArray permWrite = new JsonArray().add("mod-settings.global.write." + en.getString("scope"));

    RestAssured.given()
        .baseUri(MODULE_URL)  // if not, Okapi will intercept
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en.encode())
        .post("/settings/entries")
        .then()
        .statusCode(413)
        .contentType(ContentType.TEXT)
        .body(is("Request Entity Too Large"));
  }

  @Test
  public void testMissingPermissions() {
    JsonObject en = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("value", new JsonObject().put("v", "thevalue"));

    JsonArray permRead = new JsonArray().add("mod-settings.global.read." + en.getString("scope"));
    JsonArray permWrite = new JsonArray().add("mod-settings.global.write." + en.getString("scope"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
        .contentType(ContentType.JSON)
        .body(en.encode())
        .post("/settings/entries")
        .then()
        .statusCode(403);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
        .contentType(ContentType.JSON)
        .body(en.encode())
        .put("/settings/entries/" + en.getString("id"))
        .then()
        .statusCode(403);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en.encode())
        .post("/settings/entries")
        .then()
        .statusCode(204);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .get("/settings/entries/" + en.getString("id"))
        .then()
        .statusCode(404);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
        .delete("/settings/entries/" + en.getString("id"))
        .then()
        .statusCode(404);
  }

  @Test
  public void testNotFound() {
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, "[]")
        .get("/settings/entries/" + UUID.randomUUID())
        .then()
        .statusCode(404);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, "[]")
        .delete("/settings/entries/" + UUID.randomUUID())
        .then()
        .statusCode(404);

    JsonObject en = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("value", new JsonObject().put("v", "thevalue"));
    JsonArray permWrite = new JsonArray().add("mod-settings.global.write." + en.getString("scope"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en.encode())
        .put("/settings/entries/" + en.getString("id"))
        .then()
        .statusCode(404);
  }

  @Test
  public void testConstraintsWithoutUser() {
    JsonObject en1 = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("value", new JsonObject().put("v", "thevalue"));

    JsonArray permWrite = new JsonArray().add("mod-settings.global.write." + en1.getString("scope"));
    JsonArray permRead = new JsonArray().add("mod-settings.global.read." + en1.getString("scope"));
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en1.encode())
        .post("/settings/entries")
        .then()
        .statusCode(204);

    JsonObject en2 = en1
        .copy()
        .put("id", UUID.randomUUID().toString());

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en2.encode())
        .post("/settings/entries")
        .then()
        .statusCode(403)
        .contentType(ContentType.TEXT);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
        .get("/settings/entries/" + en2.getString("id"))
        .then()
        .statusCode(404)
        .contentType(ContentType.TEXT);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
        .get("/settings/entries/" + en1.getString("id"))
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body(is(en1.encode()));
  }

  @Test
  public void testConstraintsWithUser() {
    JsonObject en1 = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("userId", UUID.randomUUID().toString())
        .put("value", new JsonObject().put("v", "thevalue"));

    JsonArray permWrite = new JsonArray().add("mod-settings.users.write." + en1.getString("scope"));
    JsonArray permRead = new JsonArray().add("mod-settings.users.read." + en1.getString("scope"));
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en1.encode())
        .post("/settings/entries")
        .then()
        .statusCode(204);

    JsonObject en2 = en1
        .copy()
        .put("id", UUID.randomUUID().toString());

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en2.encode())
        .post("/settings/entries")
        .then()
        .statusCode(403)
        .contentType(ContentType.TEXT);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
        .get("/settings/entries/" + en2.getString("id"))
        .then()
        .statusCode(404)
        .contentType(ContentType.TEXT);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
        .get("/settings/entries/" + en1.getString("id"))
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body(is(en1.encode()));
  }

  @Test
  public void testTwoUsersSameSetting() {
    JsonObject en1 = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("userId", UUID.randomUUID().toString())
        .put("value", new JsonObject().put("v", "thevalue"));

    JsonArray permWrite = new JsonArray().add("mod-settings.users.write." + en1.getString("scope"));
    JsonArray permRead = new JsonArray().add("mod-settings.users.read." + en1.getString("scope"));
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en1.encode())
        .post("/settings/entries")
        .then()
        .statusCode(204);

    JsonObject en2 = en1
        .copy()
        .put("id", UUID.randomUUID().toString())
        .put("userId", UUID.randomUUID().toString());

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en2.encode())
        .post("/settings/entries")
        .then()
        .statusCode(204);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
        .get("/settings/entries/" + en1.getString("id"))
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body(is(en1.encode()));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
        .get("/settings/entries/" + en2.getString("id"))
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body(is(en2.encode()));
  }

  @Test
  public void testConstraintId() {
    JsonObject en = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("userId", UUID.randomUUID().toString())
        .put("value", new JsonObject().put("v", "thevalue"));

    JsonArray permWrite = new JsonArray().add("mod-settings.users.write." + en.getString("scope"));
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en.encode())
        .post("/settings/entries")
        .then()
        .statusCode(204);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en.encode())
        .post("/settings/entries")
        .then()
        .statusCode(403)
        .contentType(ContentType.TEXT);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .delete("/settings/entries/" + en.getString("id"))
        .then()
        .statusCode(204);
  }

  @Test
  public void testUpdateOk() {
    JsonObject en1 = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("userId", UUID.randomUUID().toString())
        .put("value", new JsonObject().put("v", "thevalue"));

    JsonArray permWrite = new JsonArray().add("mod-settings.users.write." + en1.getString("scope"));
    JsonArray permRead = new JsonArray().add("mod-settings.users.read." + en1.getString("scope"));
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en1.encode())
        .post("/settings/entries")
        .then()
        .statusCode(204);

    JsonObject en2 = en1
        .copy()
        .put("value", new JsonObject().put("v", "thevalue2"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en2.encode())
        .put("/settings/entries/" + en2.getString("id"))
        .then()
        .statusCode(204);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
        .get("/settings/entries/" + en2.getString("id"))
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body(is(en2.encode()));
  }

  @Test
  public void testUpdateConstraint() {
    JsonObject en1 = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("userId", UUID.randomUUID().toString())
        .put("value", new JsonObject().put("v", "thevalue"));

    JsonArray permWrite = new JsonArray().add("mod-settings.users.write." + en1.getString("scope"));
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en1.encode())
        .post("/settings/entries")
        .then()
        .statusCode(204);

    JsonObject en2 = en1
        .copy()
        .put("id", UUID.randomUUID().toString())
        .put("userId", UUID.randomUUID().toString());

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en2.encode())
        .post("/settings/entries")
        .then()
        .statusCode(204);

    JsonObject en3 = en2
        .copy()
        .put("userId", UUID.fromString(en1.getString("userId")));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en3.encode())
        .put("/settings/entries/" + en3.getString("id"))
        .then()
        .statusCode(404)
        .contentType(ContentType.TEXT);
  }

  @Test
  public void testUpdateWrongOwner() {
    JsonObject en1 = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("userId", UUID.randomUUID().toString())
        .put("value", new JsonObject().put("v", "thevalue"));

    JsonArray permWrite = new JsonArray().add("mod-settings.owner.write." + en1.getString("scope"));
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en1.encode())
        .post("/settings/entries")
        .then()
        .statusCode(403);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, UUID.randomUUID().toString())
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en1.encode())
        .post("/settings/entries")
        .then()
        .statusCode(403);
  }

  @Test
  public void testGetSettings() {
    JsonObject en = new JsonObject()
        .put("scope", UUID.randomUUID().toString())
        .put("value", new JsonObject().put("v", "thevalue"));
    JsonArray permGlobalWrite = new JsonArray().add("mod-settings.global.write." + en.getString("scope"));
    JsonArray permGlobalRead = new JsonArray().add("mod-settings.global.read." + en.getString("scope"));
    for (int i = 0; i < 15; i++) {
      en
          .put("id", UUID.randomUUID().toString())
          .put("key", "g" + i);
      RestAssured.given()
          .header(XOkapiHeaders.TENANT, TENANT_1)
          .header(XOkapiHeaders.PERMISSIONS, permGlobalWrite.encode())
          .contentType(ContentType.JSON)
          .body(en.encode())
          .post("/settings/entries")
          .then()
          .statusCode(204);
    }

    JsonObject en2 = new JsonObject()
        .put("scope", en.getString("scope"))
        .put("value", new JsonObject().put("v", "thevalue"));
    JsonArray permUsersWrite = new JsonArray().add("mod-settings.users.write." + en.getString("scope"));
    JsonArray permUsersRead = new JsonArray().add("mod-settings.users.read." + en.getString("scope"));
    for (int i = 0; i < 3; i++) {
      en2
          .put("id", UUID.randomUUID().toString())
          .put("userId", UUID.randomUUID().toString())
          .put("key", "k1");
      RestAssured.given()
          .header(XOkapiHeaders.TENANT, TENANT_1)
          .header(XOkapiHeaders.PERMISSIONS, permUsersWrite.encode())
          .contentType(ContentType.JSON)
          .body(en2.encode())
          .post("/settings/entries")
          .then()
          .statusCode(204);
    }

    UUID userId = UUID.randomUUID();
    JsonObject en3 = new JsonObject()
        .put("scope", en.getString("scope"))
        .put("value", new JsonObject().put("v", "thevalue"));
    JsonArray permOwnerWrite = new JsonArray().add("mod-settings.owner.write." + en.getString("scope"));
    JsonArray permOwnerRead = new JsonArray().add("mod-settings.owner.read." + en.getString("scope"));
    for (int i = 0; i < 2; i++) {
      en3
          .put("id", UUID.randomUUID().toString())
          .put("userId", userId)
          .put("key", "l" + i);
      RestAssured.given()
          .header(XOkapiHeaders.TENANT, TENANT_1)
          .header(XOkapiHeaders.USER_ID, userId.toString())
          .header(XOkapiHeaders.PERMISSIONS, permOwnerWrite.encode())
          .contentType(ContentType.JSON)
          .body(en3.encode())
          .post("/settings/entries")
          .then()
          .statusCode(204);
    }

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permGlobalRead.encode())
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("items", hasSize(10))
        .body("resultInfo.totalRecords", is(15));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permGlobalRead.encode())
        .queryParam("limit", 3)
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("items", hasSize(3))
        .body("resultInfo.totalRecords", is(15));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permGlobalRead.encode())
        .queryParam("offset", 12)
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("items", hasSize(3))
        .body("resultInfo.totalRecords", is(15));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permUsersRead.encode())
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("items", hasSize(5))
        .body("resultInfo.totalRecords", is(5));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerRead.encode())
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("items", hasSize(2))
        .body("resultInfo.totalRecords", is(2));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerRead.encode())
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("items", hasSize(2))
        .body("resultInfo.totalRecords", is(2));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerRead.encode())
        .queryParam("query", "scope=\""
            + en3.getString("scope") + "\" and key=l1")
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("items", hasSize(1))
        .body("resultInfo.totalRecords", is(1));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerRead.encode())
        .queryParam("query", "scope=\""
            + en3.getString("scope") + "\" and key=l*")
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("items", hasSize(2))
        .body("resultInfo.totalRecords", is(2));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, new JsonArray().encode())
        .get("/settings/entries")
        .then()
        .statusCode(403)
        .contentType(ContentType.TEXT);
  }

  @Test
  public void testGetSettingsStream() {
    JsonObject en = new JsonObject()
        .put("scope", UUID.randomUUID().toString())
        .put("value", new JsonObject().put("v", "stream"));
    JsonArray permGlobalWrite = new JsonArray().add("mod-settings.global.write." + en.getString("scope"));
    JsonArray permGlobalRead = new JsonArray().add("mod-settings.global.read." + en.getString("scope"));
    for (int i = 0; i < 201; i++) {
      en
          .put("id", UUID.randomUUID().toString())
          .put("key", "s" + i);
      RestAssured.given()
          .header(XOkapiHeaders.TENANT, TENANT_1)
          .header(XOkapiHeaders.PERMISSIONS, permGlobalWrite.encode())
          .contentType(ContentType.JSON)
          .body(en.encode())
          .post("/settings/entries")
          .then()
          .statusCode(204);
    }

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permGlobalRead.encode())
        .get("/settings/entries?limit=1000")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("items", hasSize(201))
        .body("resultInfo.totalRecords", is(201));
  }

  @Test
  public void testUploadFailures() {
    String scope = UUID.randomUUID().toString();
    UUID userId = UUID.randomUUID();
    JsonArray permissionsLacking = new JsonArray().add("mod-settings.owner.write.other");
    JsonArray permOwnerWrite = new JsonArray().add("mod-settings.owner.write." + scope);
    int no = 3;
    JsonArray ar = new JsonArray();
    for (int i = 0; i < no; i++) {
      JsonObject en = new JsonObject()
          .put("scope", scope)
          .put("key", "k" + i)
          .put("userId", userId.toString())
          .put("value", new JsonObject().put("v", "thevalue"));
      ar.add(en);
    }
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permissionsLacking.encode())
        .contentType(ContentType.JSON)
        .body(ar.encode())
        .put("/settings/upload")
        .then()
        .statusCode(403)
        .contentType(ContentType.TEXT)
        .body(is("Forbidden"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .contentType(ContentType.JSON)
        .body(ar.encode())
        .put("/settings/upload")
        .then()
        .statusCode(400)
        .contentType(ContentType.TEXT)
        .body(is("Missing header X-Okapi-Permissions"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permissionsLacking.encode())
        .contentType(ContentType.JSON)
        .body(ar.encode())
        .put("/settings/upload")
        .then()
        .statusCode(403)
        .contentType(ContentType.TEXT)
        .body(is("Forbidden"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerWrite.encode())
        .contentType(ContentType.TEXT)
        .body("Hello")
        .put("/settings/upload")
        .then()
        .statusCode(400)
        .contentType(ContentType.TEXT)
        .body(is("Content-Type must be application/json"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerWrite.encode())
        .contentType(ContentType.JSON)
        .body("[{\"a\":\"b\"}]")
        .put("/settings/upload")
        .then()
        .statusCode(403)
        .contentType(ContentType.TEXT)
        .body(is("Forbidden"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerWrite.encode())
        .contentType(ContentType.JSON)
        .body("[{\"a\":]")
        .put("/settings/upload")
        .then()
        .statusCode(400)
        .contentType(ContentType.TEXT)
        .body(containsString("Unexpected character"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerWrite.encode())
        .put("/settings/upload")
        .then()
        .statusCode(400)
        .contentType(ContentType.TEXT)
        .body(is("Content-Type must be application/json"));
  }

  @Test
  public void uploadIdMustNotBeSupplied() {
    String scope = UUID.randomUUID().toString();
    UUID userId = UUID.randomUUID();
    JsonArray permOwnerWrite = new JsonArray().add("mod-settings.owner.write." + scope);
    int no = 2;
    JsonArray ar = new JsonArray();
    for (int i = 0; i < no; i++) {
      JsonObject en = new JsonObject()
          .put("id", UUID.randomUUID().toString())
          .put("scope", scope)
          .put("key", "k" + i)
          .put("userId", userId.toString())
          .put("value", "thevalue");
      ar.add(en);
    }
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerWrite.encode())
        .contentType(ContentType.JSON)
        .body(ar.encode())
        .put("/settings/upload")
        .then()
        .statusCode(400)
        .contentType(ContentType.TEXT)
        .body(is("No id must supplied for upload"));
  }

  @Test
  public void testUploadOK() {
    String scope = UUID.randomUUID().toString();
    UUID userId = UUID.randomUUID();
    JsonArray permOwnerWrite = new JsonArray().add("mod-settings.owner.write." + scope);
    JsonArray permOwnerRead = new JsonArray().add("mod-settings.owner.read." + scope);
    int no = 100;
    JsonArray ar = new JsonArray();
    for (int i = 0; i < no; i++) {
      JsonObject en = new JsonObject()
          .put("scope", scope)
          .put("key", "k" + i)
          .put("userId", userId.toString())
          .put("value", "s".repeat(1000));
      ar.add(en);
    }
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerWrite.encode())
        .contentType(ContentType.JSON)
        .body(ar.encode())
        .put("/settings/upload")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("inserted", is(no))
        .body("updated", is(0));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerWrite.encode())
        .contentType(ContentType.JSON)
        .body(ar.encode())
        .put("/settings/upload")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("inserted", is(0))
        .body("updated", is(no));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerRead.encode())
        .queryParam("limit", 0)
        .contentType(ContentType.JSON)
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("items", hasSize(0))
        .body("resultInfo.totalRecords", is(no));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.USER_ID, userId.toString())
        .header(XOkapiHeaders.PERMISSIONS, permOwnerRead.encode())
        .queryParam("limit", 0)
        .contentType(ContentType.JSON)
        .get("/settings/entries")
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body("items", hasSize(0))
        .body("resultInfo.totalRecords", is(no));
  }
}
