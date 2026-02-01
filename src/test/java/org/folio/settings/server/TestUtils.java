package org.folio.settings.server;

import static org.hamcrest.Matchers.is;

import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;

public class TestUtils {

  public static Future<Void> postTenant(Vertx vertx, String okapiUrl, String tenant, String moduleTo) {
    return vertx.executeBlocking(() -> {
      postTenant(okapiUrl, tenant, moduleTo);
      return null;
    });
  }

  public static void postTenant(String okapiUrl, String tenant, String moduleTo) {
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

}
