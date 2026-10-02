package org.folio.settings.server;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.restassured.RestAssured;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import org.folio.okapi.common.ChattyHttpResponseExpectation;

public class TestUtils {

  private static final String BASE_URI = "http://localhost:8081";

  public static Future<Void> postTenant(Vertx vertx, String tenant, String moduleTo) {
    var webClient = WebClient.create(vertx);
    return webClient.postAbs(BASE_URI + "/_/tenant")
        .putHeader("X-Okapi-Url", BASE_URI)
        .putHeader("X-Okapi-Tenant", tenant)
        .sendJsonObject(JsonObject.of("module_to", moduleTo))
        .expecting(ChattyHttpResponseExpectation.SC_CREATED)
        .compose(response -> {
          var id = response.bodyAsJsonObject().getString("id");
          return webClient.getAbs(BASE_URI + "/_/tenant/" + id + "?wait=30000")
              .putHeader("X-Okapi-Tenant", tenant)
              .send()
              .expecting(ChattyHttpResponseExpectation.SC_OK);
        })
        .map(response -> {
          var body = response.bodyAsJsonObject();
          assertThat(body.getBoolean("complete"), is(true));
          return (Void)null;
        })
        .onComplete(x -> webClient.close());
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
        .body("complete", is(true))
        .body("error", is(nullValue()));
  }

}
