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
import org.folio.settings.server.data.LocaleSettings;
import org.folio.settings.server.storage.LocaleStorage;
import org.folio.tlib.util.TenantUtil;

public final class LocaleService {
  private LocaleService() {
  }

  /**
   * Send 200 response with locale from database.
   */
  public static Future<Void> getLocale(RoutingContext ctx) {
    return new LocaleStorage(ctx.vertx(), TenantUtil.tenant(ctx))
        .getLocale()
        .map(localeSettings -> {
          HttpResponse.responseJson(ctx, HTTP_OK)
              .end(JsonObject.mapFrom(localeSettings).encode());
          return null;
        });
  }

  /**
   * Write JSON body to database as new locale.
   */
  public static Future<Void> setLocale(RoutingContext ctx) {
    var localeSettings = ctx.body().asJsonObject().mapTo(LocaleSettings.class);
    if (isBlank(localeSettings.getLocale())) {
      response400(ctx, "locale missing");
    } else if (isBlank(localeSettings.getTimezone())) {
      response400(ctx, "timezone missing");
    } else if (isBlank(localeSettings.getCurrency())) {
      response400(ctx, "currency missing");
    } else if (!"latn".equals(localeSettings.getNumberingSystem())
        && !"arab".equals(localeSettings.getNumberingSystem())) {
      response400(ctx, "numberingSystem must be latn or arab");
    } else {
      return new LocaleStorage(ctx.vertx(), TenantUtil.tenant(ctx))
          .updateLocale(localeSettings)
          .compose(x -> HttpResponse.responseText(ctx, HTTP_CREATED).end(),
              e -> HttpResponse.responseText(ctx, HTTP_INTERNAL_ERROR).end());
    }
    return Future.succeededFuture();
  }

  private static void response400(RoutingContext ctx, String msg) {
    HttpResponse.responseText(ctx, HTTP_BAD_REQUEST).end(msg);
  }
}
