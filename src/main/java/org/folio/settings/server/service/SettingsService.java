package org.folio.settings.server.service;

import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;

import io.vertx.core.Future;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import java.util.List;
import java.util.UUID;
import org.folio.okapi.common.HttpResponse;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.settings.server.data.Entry;
import org.folio.settings.server.storage.SettingsStorage;
import org.folio.settings.server.storage.UserException;
import org.folio.settings.server.util.UserUtil;
import org.folio.tlib.util.TenantUtil;

public final class SettingsService {

  private static final int DEFAULT_LIMIT = 10;

  private SettingsService() {
  }

  static SettingsStorage create(RoutingContext ctx) {
    // get user Id
    UUID currentUserId = UserUtil.getUserId(ctx);

    // get permissions which is required in OpenAPI spec
    String okapiPermissions = ctx.request().getHeader(XOkapiHeaders.PERMISSIONS);
    if (okapiPermissions == null) {
      throw new UserException("Missing header " + XOkapiHeaders.PERMISSIONS);
    }
    var permissions = new JsonArray(okapiPermissions);
    var tenant = TenantUtil.tenant(ctx);
    return new SettingsStorage(ctx.vertx(), tenant, currentUserId, permissions);
  }

  /**
   * Write setting to database.
   */
  public static Future<Void> postSetting(RoutingContext ctx) {
    SettingsStorage storage = create(ctx);
    Entry entry = ctx.body().asJsonObject().mapTo(Entry.class);
    entry.validate();
    return storage.createEntry(entry)
        .map(entity -> {
          ctx.response().setStatusCode(HTTP_NO_CONTENT);
          ctx.response().end();
          return null;
        });
  }

  /**
   * Return setting from database.
   */
  public static Future<Void> getSetting(RoutingContext ctx) {
    SettingsStorage storage = create(ctx);
    String id = ctx.pathParam("id");
    return storage.getEntry(UUID.fromString(id))
        .map(entity -> {
          HttpResponse.responseJson(ctx, HTTP_OK)
              .end(JsonObject.mapFrom(entity).encode());
          return null;
        });
  }

  /**
   * Update setting in database.
   */
  public static Future<Void> updateSetting(RoutingContext ctx) {
    var entry = ctx.body().asJsonObject().mapTo(Entry.class);
    entry.validate();
    var id = UUID.fromString(ctx.pathParam("id"));
    if (!id.equals(entry.getId())) {
      return Future.failedFuture(new UserException("id mismatch"));
    }
    var settingsStorage = create(ctx);
    return settingsStorage.updateEntry(entry)
        .map(entity -> {
          ctx.response().setStatusCode(HTTP_NO_CONTENT);
          ctx.response().end();
          return null;
        });
  }

  /**
   * Delete setting in database.
   */
  public static Future<Void> deleteSetting(RoutingContext ctx) {
    SettingsStorage configStorage = create(ctx);
    String id = ctx.pathParam("id");
    return configStorage.deleteEntry(UUID.fromString(id))
        .map(res -> {
          ctx.response().setStatusCode(HTTP_NO_CONTENT);
          ctx.response().end();
          return null;
        });
  }

  /**
   * Find settings using CQL query.
   */
  public static Future<Void> getSettings(RoutingContext ctx) {
    SettingsStorage storage = create(ctx);
    List<String> tmp = ctx.queryParam("query");
    String query = tmp.isEmpty() ? null : tmp.get(0);
    tmp = ctx.queryParam("limit");
    int limit = tmp.isEmpty() ? DEFAULT_LIMIT : Integer.parseInt(tmp.get(0));
    tmp = ctx.queryParam("offset");
    int offset = tmp.isEmpty() ? 0 : Integer.parseInt(tmp.get(0));
    return storage.getEntries(ctx.response(), query, offset, limit);
  }
}
