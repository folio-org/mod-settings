package org.folio.config.server.main;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.server.TestBase;
import org.folio.okapi.common.XOkapiHeaders;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasLength;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.startsWith;

@RunWith(VertxUnitRunner.class)
public class MainVerticleTest extends TestBase {
  private final static Logger log = LogManager.getLogger("MainVerticleTest");

  @Test
  public void testAdminHealth() {
    RestAssured.given()
        .baseUri(MODULE_URL)
        .get("/admin/health")
        .then().statusCode(200)
        .header("Content-Type", is("text/plain"));
  }

  @Ignore
  @Test
  public void testCrud() {
    JsonObject en = new JsonObject()
        .put("id", UUID.randomUUID().toString())
        .put("scope", "s1")
        .put("key", "k1")
        .put("value", "v1");
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .contentType(ContentType.JSON)
        .body(en.encode())
        .post("/config/entries")
        .then()
        .statusCode(204);
    RestAssured.given()
        .header(XOkapiHeaders.TENANT, TENANT_1)
        .get("/config/entries/" + en.getString("id"))
        .then()
        .statusCode(200)
        .contentType(ContentType.JSON);
  }
}
