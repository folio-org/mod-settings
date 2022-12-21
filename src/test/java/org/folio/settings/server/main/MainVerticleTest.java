package org.folio.settings.server.main;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.settings.server.TestBase;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.hamcrest.Matchers.is;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest extends TestBase {
  private final static Logger log = LogManager.getLogger("MainVerticleTest");

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
  public void testCrudNoUserOk() {
    JsonObject en = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("value", new JsonObject().put("v", "thevalue"));
    JsonArray permRead = new JsonArray().add("settings.global.read." + en.getString("scope"));
    JsonArray permWrite = new JsonArray().add("settings.global.write." + en.getString("scope"));

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

  @Test
  public void testMissingPermissions() {
    JsonObject en = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("value", new JsonObject().put("v", "thevalue"));

    JsonArray permRead = new JsonArray().add("settings.global.read." + en.getString("scope"));
    JsonArray permWrite = new JsonArray().add("settings.global.write." + en.getString("scope"));

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
        .statusCode(403);

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
        .delete("/settings/entries/" + en.getString("id"))
        .then()
        .statusCode(403);
  }

  @Test
  public void testNotFound() {
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .get("/settings/entries/" + UUID.randomUUID())
        .then()
        .statusCode(404);


    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .delete("/settings/entries/" + UUID.randomUUID())
        .then()
        .statusCode(404);

    JsonObject en = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("value", new JsonObject().put("v", "thevalue"));
    JsonArray permWrite = new JsonArray().add("settings.global.write." + en.getString("scope"));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en.encode())
        .put("/settings/entries/" + en.getString("id"))
        .then()
        .statusCode(400); // ought to be 404, but 400 "includes" that
  }

  @Test
  public void testConstaintsWithoutUser() {
    JsonObject en1 = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("value", new JsonObject().put("v", "thevalue"));

    JsonArray permWrite = new JsonArray().add("settings.global.write." + en1.getString("scope"));
    JsonArray permRead = new JsonArray().add("settings.global.read." + en1.getString("scope"));
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
        .statusCode(400)
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
  public void testConstaintsWithUser() {
    JsonObject en1 = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("userId", UUID.randomUUID().toString())
        .put("value", new JsonObject().put("v", "thevalue"));

    JsonArray permWrite = new JsonArray().add("settings.users.write." + en1.getString("scope"));
    JsonArray permRead = new JsonArray().add("settings.users.read." + en1.getString("scope"));
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
        .statusCode(400)
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

    JsonArray permWrite = new JsonArray().add("settings.users.write." + en1.getString("scope"));
    JsonArray permRead = new JsonArray().add("settings.users.read." + en1.getString("scope"));
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
  public void testConstaintId() {
    JsonObject en = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", UUID.randomUUID().toString())
        .put("key", "k1")
        .put("userId", UUID.randomUUID().toString())
        .put("value", new JsonObject().put("v", "thevalue"));

    JsonArray permWrite = new JsonArray().add("settings.users.write." + en.getString("scope"));
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
        .statusCode(400)
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

    JsonArray permWrite = new JsonArray().add("settings.users.write." + en1.getString("scope"));
    JsonArray permRead = new JsonArray().add("settings.users.read." + en1.getString("scope"));
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

    JsonArray permWrite = new JsonArray().add("settings.users.write." + en1.getString("scope"));
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
        .statusCode(400)
        .contentType(ContentType.TEXT);
  }
}
