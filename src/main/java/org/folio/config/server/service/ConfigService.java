package org.folio.config.server.service;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.RouterBuilder;
import io.vertx.ext.web.validation.RequestParameter;
import io.vertx.ext.web.validation.RequestParameters;
import io.vertx.ext.web.validation.ValidationHandler;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.server.data.Entry;
import org.folio.config.server.storage.ConfigStorage;
import org.folio.config.server.storage.ForbiddenException;
import org.folio.config.server.storage.NotFoundException;
import org.folio.config.server.storage.UserException;
import org.folio.okapi.common.HttpResponse;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.TenantInitHooks;

public class ConfigService implements RouterCreator, TenantInitHooks {

  public static final int BODY_LIMIT = 65536; // 64 kb

  private static final Logger log = LogManager.getLogger("ConfigService");

  @Override
  public Future<Router> createRouter(Vertx vertx) {
    return RouterBuilder.create(vertx, "openapi/config.yaml")
        .map(routerBuilder -> {
          // https://vertx.io/docs/vertx-web/java/#_limiting_body_size
          routerBuilder.rootHandler(BodyHandler.create().setBodyLimit(BODY_LIMIT));
          handlers(vertx, routerBuilder);
          return routerBuilder.createRouter();
        });
  }

  private void failureHandler(RoutingContext ctx) {
    ctx.response().setStatusCode(ctx.statusCode());
    ctx.response().putHeader(HttpHeaders.CONTENT_TYPE, "text/plain");
    ctx.response().end(HttpResponseStatus.valueOf(ctx.statusCode()).reasonPhrase());
  }

  void commonError(RoutingContext ctx, Throwable cause) {
    log.error("commonError class={} {}", cause.getClass(), cause.getMessage());
    if (cause instanceof ForbiddenException) {
      HttpResponse.responseError(ctx, 403, cause.getMessage());
    } else if (cause instanceof NotFoundException) {
      HttpResponse.responseError(ctx, 404, cause.getMessage());
    } else if (cause instanceof UserException) {
      HttpResponse.responseError(ctx, 400, cause.getMessage());
    } else {
      HttpResponse.responseError(ctx, 500, cause.getMessage());
    }
  }

  private void handlers(Vertx vertx, RouterBuilder routerBuilder) {
    routerBuilder
        .operation("getConfigurationEntries")
        .handler(ctx -> getConfigurationEntries(vertx, ctx)
            .onFailure(cause -> commonError(ctx, cause))
        )
        .failureHandler(this::failureHandler);

    routerBuilder
        .operation("postConfigurationEntry")
        .handler(ctx -> postConfigurationEntry(vertx, ctx)
            .onFailure(cause -> commonError(ctx, cause))
        )
        .failureHandler(this::failureHandler);
    routerBuilder
        .operation("getConfigurationEntry")
        .handler(ctx -> getConfigurationEntry(vertx, ctx)
            .onFailure(cause -> commonError(ctx, cause))
        )
        .failureHandler(this::failureHandler);
    routerBuilder
        .operation("putConfigurationEntry")
        .handler(ctx -> updateConfigurationEntry(vertx, ctx)
            .onFailure(cause -> commonError(ctx, cause))
        )
        .failureHandler(this::failureHandler);
    routerBuilder
        .operation("deleteConfigurationEntry")
        .handler(ctx -> deleteConfigurationEntry(vertx, ctx)
            .onFailure(cause -> commonError(ctx, cause))
        )
        .failureHandler(this::failureHandler);
  }

  /**
   * Helper to create ConfigStorage from routing context.
   * @param ctx rouging context.
   * @return ConfigStorage instance
   */
  public static ConfigStorage create(RoutingContext ctx) {
    RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    // get tenant
    RequestParameter tenantParameter = params.headerParameter(XOkapiHeaders.TENANT);
    if (tenantParameter == null) {
      throw new RuntimeException("tenant required");
    }
    String tenant = tenantParameter.getString();

    // get user Id
    RequestParameter userIdParameter = params.headerParameter(XOkapiHeaders.USER_ID);
    UUID currentUserId = null;
    if (userIdParameter != null) {
      currentUserId = UUID.fromString(userIdParameter.getString());
    }

    // get permissions
    RequestParameter okapiPermissions = params.headerParameter(XOkapiHeaders.PERMISSIONS);
    JsonArray permissions;
    if (okapiPermissions != null) {
      permissions = new JsonArray(okapiPermissions.getString());
    } else {
      permissions = new JsonArray();
    }
    return new ConfigStorage(ctx.vertx(), tenant, currentUserId, permissions);
  }

  Future<Void> getConfigurationEntries(Vertx vertx, RoutingContext ctx) {
    return Future.failedFuture("Not implemented");
  }

  Future<Void> postConfigurationEntry(Vertx vertx, RoutingContext ctx) {
    try {
      ConfigStorage configStorage = create(ctx);
      RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
      RequestParameter body = params.body();
      Entry entry = body.getJsonObject().mapTo(Entry.class);
      return configStorage.createEntry(entry)
          .map(entity -> {
            ctx.response().setStatusCode(204);
            ctx.response().end();
            return null;
          });
    } catch (Exception e) {
      log.error("{}", e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  Future<Void> getConfigurationEntry(Vertx vertx, RoutingContext ctx) {
    try {
      ConfigStorage configStorage = create(ctx);
      RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
      String id  = params.pathParameter("id").getString();
      return configStorage.getEntry(UUID.fromString(id))
          .map(entity -> {
            HttpResponse.responseJson(ctx, 200)
                    .end(JsonObject.mapFrom(entity).encode());
            return null;
          });
    } catch (Exception e) {
      log.error("{}", e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  Future<Void> updateConfigurationEntry(Vertx vertx, RoutingContext ctx) {
    try {
      ConfigStorage configStorage = create(ctx);
      RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
      RequestParameter body = params.body();
      Entry entry = body.getJsonObject().mapTo(Entry.class);
      UUID id  = UUID.fromString(params.pathParameter("id").getString());
      if (!id.equals(entry.getId())) {
        return Future.failedFuture(new UserException("id mismatch"));
      }
      return configStorage.updateEntry(entry)
          .map(entity -> {
            ctx.response().setStatusCode(204);
            ctx.response().end();
            return null;
          });
    } catch (Exception e) {
      log.error("{}", e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  Future<Void> deleteConfigurationEntry(Vertx vertx, RoutingContext ctx) {
    try {
      ConfigStorage configStorage = create(ctx);
      RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
      String id  = params.pathParameter("id").getString();
      return configStorage.deleteEntry(UUID.fromString(id))
          .map(res -> {
            ctx.response().setStatusCode(204);
            ctx.response().end();
            return null;
          });
    } catch (Exception e) {
      log.error("{}", e.getMessage(), e);
      return Future.failedFuture(e);
    }
  }

  @Override
  public Future<Void> postInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
    if (!tenantAttributes.containsKey("module_to")) {
      return Future.succeededFuture(); // doing nothing for disable
    }
    ConfigStorage storage = new ConfigStorage(vertx, tenant, null, null);
    return storage.init();
  }

  @Override
  public Future<Void> preInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
    return Future.succeededFuture();
  }
}
