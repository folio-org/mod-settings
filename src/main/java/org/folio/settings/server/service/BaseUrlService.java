package org.folio.settings.server.service;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.settings.server.util.StringUtil.isBlank;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import org.folio.okapi.common.HttpResponse;
import org.folio.settings.server.storage.BaseUrlStorage;
import org.folio.tlib.util.TenantUtil;

public final class BaseUrlService {
  private BaseUrlService() {
  }

  /**
   * Send 200 response with baseUrl from database.
   */
  public static Future<Void> getBaseUrl(RoutingContext ctx) {
    return new BaseUrlStorage(ctx.vertx(), TenantUtil.tenant(ctx))
        .getBaseUrl()
        .map(baseUrl -> {
          HttpResponse.responseJson(ctx, HTTP_OK)
              .end(JsonObject.of("baseUrl", baseUrl).encode());
          return null;
        });
  }

  /**
   * Write new baseUrl to database.
   */
  public static Future<Void> setBaseUrl(RoutingContext ctx) {
    var baseUrl = ctx.body().asJsonObject().getString("baseUrl");
    if (isBlank(baseUrl)) {
      response400(ctx, "baseUrl is missing");
    } else if (baseUrl.endsWith("/")) {
      response400(ctx, "baseUrl must not end with a slash");
    } else {
      return new BaseUrlStorage(ctx.vertx(), TenantUtil.tenant(ctx))
          .updateBaseUrl(baseUrl)
          .compose(x -> HttpResponse.responseText(ctx, HTTP_CREATED).end(),
              e -> HttpResponse.responseText(ctx, HTTP_INTERNAL_ERROR).end());
    }
    return Future.succeededFuture();
  }

  private static void response400(RoutingContext ctx, String msg) {
    HttpResponse.responseText(ctx, HTTP_BAD_REQUEST).end(msg);
  }
}
