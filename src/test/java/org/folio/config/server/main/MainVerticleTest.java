package org.folio.config.server.main;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.server.TestBase;
import org.folio.okapi.common.XOkapiHeaders;
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
  public void testCrudNoUser() {
    JsonObject en = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", "s1")
        .put("key", "k1")
        .put("value", new JsonObject().put("v", "thevalue"));
    JsonArray permWrite = new JsonArray().add("config.global.write.s1");
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permWrite.encode())
        .contentType(ContentType.JSON)
        .body(en.encode())
        .post("/config/entries")
        .then()
        .statusCode(204);

    JsonArray permRead = new JsonArray().add("config.global.read.s1");
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .header(XOkapiHeaders.PERMISSIONS, permRead.encode())
        .get("/config/entries/" + en.getString("id"))
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON)
        .body(is(en.encode()));

    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .get("/config/entries")
        .then()
        .statusCode(500)
        .contentType(ContentType.TEXT)
        .body(is("Not implemented"));
  }
}
