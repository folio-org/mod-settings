package org.folio.settings.server.service;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_CONFLICT;
import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.settings.server.util.StringUtil.isBlank;

import org.folio.okapi.common.HttpResponse;
import org.folio.settings.server.data.TenantAddress;
import org.folio.settings.server.storage.TenantAddressesStorage;
import org.folio.tlib.util.TenantUtil;

import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgException;

public final class TenantAddressesService {

  private static final int DEFAULT_LIMIT = 50;
  private static final int DEFAULT_OFFSET = 0;

  private TenantAddressesService() {
  }

  /**
   * Send 200 response with tenant addresses from database.
   */
  public static Future<Void> getTenantAddresses(RoutingContext ctx) {
    var limit = getIntQuery(ctx, "limit", DEFAULT_LIMIT);
    var offset = getIntQuery(ctx, "offset", DEFAULT_OFFSET);
    return new TenantAddressesStorage(ctx.vertx(), TenantUtil.tenant(ctx))
        .getTenantAddresses(offset, limit)
        .map(tenantAddresses -> {
          HttpResponse.responseJson(ctx, HTTP_OK).end(JsonObject.mapFrom(tenantAddresses).encode());
          return null;
        });
  }

  /**
   * Send 200 response with tenant address by id.
   */
  public static Future<Void> getTenantAddress(RoutingContext ctx) {
    var id = ctx.pathParam("id");
    return new TenantAddressesStorage(ctx.vertx(), TenantUtil.tenant(ctx))
        .getTenantAddress(id)
        .map(address -> {
          HttpResponse.responseJson(ctx, HTTP_OK).end(JsonObject.mapFrom(address).encode());
          return null;
        });
  }

  /**
   * Create tenant address.
   */
  public static Future<Void> createTenantAddress(RoutingContext ctx) {
    var tenantAddress = ctx.body().asJsonObject().mapTo(TenantAddress.class);
    if (addressInvalid(tenantAddress)) {
      response400(ctx, "name or address missing");
      return Future.succeededFuture();
    }

    return new TenantAddressesStorage(ctx.vertx(), TenantUtil.tenant(ctx))
        .createTenantAddress(tenantAddress)
        .map(created -> {
          HttpResponse.responseJson(ctx, HTTP_CREATED).end(JsonObject.mapFrom(created).encode());
          return (Void) null;
        }).recover(cause -> handleException(ctx, cause));
  }

  /**
   * Update tenant address.
   */
  public static Future<Void> updateTenantAddress(RoutingContext ctx) {
    var id = ctx.pathParam("id");
    var tenantAddress = ctx.body().asJsonObject().mapTo(TenantAddress.class);
    if (addressInvalid(tenantAddress)) {
      response400(ctx, "name or address missing");
      return Future.succeededFuture();
    }
    return new TenantAddressesStorage(ctx.vertx(), TenantUtil.tenant(ctx))
        .updateTenantAddress(id, tenantAddress)
        .map(x -> {
          HttpResponse.responseText(ctx, HTTP_NO_CONTENT).end();
          return (Void) null;
        }).recover(cause -> handleException(ctx, cause));
  }

  /**
   * Delete tenant address.
   */
  public static Future<Void> deleteTenantAddress(RoutingContext ctx) {
    var id = ctx.pathParam("id");
    return new TenantAddressesStorage(ctx.vertx(), TenantUtil.tenant(ctx))
        .deleteTenantAddress(id)
        .map(x -> {
          HttpResponse.responseText(ctx, HTTP_NO_CONTENT).end();
          return null;
        });
  }

  private static int getIntQuery(RoutingContext ctx, String name, int defaultValue) {
    var params = ctx.queryParam(name);
    return params.isEmpty() ? defaultValue : Integer.parseInt(params.getFirst());
  }

  private static boolean addressInvalid(TenantAddress address) {
    return address == null
        || isBlank(address.getName())
        || isBlank(address.getAddress());
  }

  private static Future<Void> handleException(RoutingContext ctx, Throwable cause) {
    if (cause instanceof PgException pgException && "23505".equals(pgException.getSqlState())) {
      HttpResponse.responseText(ctx, HTTP_CONFLICT).end("name already exists");
      return Future.succeededFuture();
    }
    return Future.failedFuture(cause);
  }

  private static void response400(RoutingContext ctx, String msg) {
    HttpResponse.responseText(ctx, HTTP_BAD_REQUEST).end(msg);
  }

}
