package org.folio.settings.server.service;

import static org.folio.HttpStatus.HTTP_BAD_REQUEST;
import static org.folio.HttpStatus.HTTP_CREATED;
import static org.folio.HttpStatus.HTTP_NO_CONTENT;
import static org.folio.HttpStatus.HTTP_OK;
import static org.folio.settings.server.util.StringUtil.isBlank;
import static org.folio.settings.server.util.TimeUtil.getTruncatedOffsetDateTime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.vertx.core.Future;
import io.vertx.ext.web.RoutingContext;
import io.vertx.pgclient.PgException;
import org.folio.HttpStatus;
import org.folio.okapi.common.HttpResponse;
import org.folio.settings.server.data.Metadata;
import org.folio.settings.server.data.TenantAddress;
import org.folio.settings.server.storage.TenantAddressesStorage;
import org.folio.settings.server.util.TimeUtil;
import org.folio.settings.server.util.UserUtil;
import org.folio.tlib.util.TenantUtil;

public final class TenantAddressesService {

  private static final int DEFAULT_LIMIT = 50;
  private static final int DEFAULT_OFFSET = 0;

  private static final ObjectMapper objectMapper = new ObjectMapper()
      .registerModule(TimeUtil.createJavaTimeModule())
      .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

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
        .onSuccess(tenantAddresses -> {
          try {
            HttpResponse.responseJson(ctx, HTTP_OK.toInt())
                .end(objectMapper.writeValueAsString(tenantAddresses));
          } catch (JsonProcessingException e) {
            response400(ctx, "Error processing JSON");
          }
        })
        .mapEmpty();
  }

  /**
   * Send 200 response with tenant address by id.
   */
  public static Future<Void> getTenantAddress(RoutingContext ctx) {
    var id = ctx.pathParam("id");
    return new TenantAddressesStorage(ctx.vertx(), TenantUtil.tenant(ctx))
        .getTenantAddress(id)
        .onSuccess(address -> {
          try {
            HttpResponse.responseJson(ctx, HTTP_OK.toInt())
                .end(objectMapper.writeValueAsString(address));
          } catch (JsonProcessingException e) {
            response400(ctx, "Error processing JSON");
          }
        })
        .mapEmpty();
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
    tenantAddress.setMetadata(new Metadata(UserUtil.getUserId(ctx), getTruncatedOffsetDateTime()));
    return new TenantAddressesStorage(ctx.vertx(), TenantUtil.tenant(ctx))
        .createTenantAddress(tenantAddress)
        .onSuccess(created -> {
          try {
            HttpResponse.responseJson(ctx, HTTP_CREATED.toInt())
                .end(objectMapper.writeValueAsString(created));
          } catch (JsonProcessingException e) {
            response400(ctx, "Error processing JSON");
          }
        })
        .<Void>mapEmpty()
        .recover(cause -> handleException(ctx, cause));
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
    tenantAddress.setMetadata(new Metadata(UserUtil.getUserId(ctx), getTruncatedOffsetDateTime()));
    return new TenantAddressesStorage(ctx.vertx(), TenantUtil.tenant(ctx))
        .updateTenantAddress(id, tenantAddress)
        .onSuccess(x -> HttpResponse.responseText(ctx, HTTP_NO_CONTENT.toInt()).end())
        .<Void>mapEmpty()
        .recover(cause -> handleException(ctx, cause));
  }

  /**
   * Delete tenant address.
   */
  public static Future<Void> deleteTenantAddress(RoutingContext ctx) {
    var id = ctx.pathParam("id");
    return new TenantAddressesStorage(ctx.vertx(), TenantUtil.tenant(ctx))
        .deleteTenantAddress(id)
        .onSuccess(x -> HttpResponse.responseText(ctx, HTTP_NO_CONTENT.toInt()).end());
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
      HttpResponse.responseText(ctx, HttpStatus.HTTP_UNPROCESSABLE_ENTITY.toInt())
          .end("name already exists");
      return Future.succeededFuture();
    }
    return Future.failedFuture(cause);
  }

  private static void response400(RoutingContext ctx, String msg) {
    HttpResponse.responseText(ctx, HTTP_BAD_REQUEST.toInt()).end(msg);
  }
}
