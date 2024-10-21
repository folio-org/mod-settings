package org.folio.settings.server.service;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpServerRequest;
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
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.HttpResponse;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.settings.server.data.Entry;
import org.folio.settings.server.storage.ForbiddenException;
import org.folio.settings.server.storage.NotFoundException;
import org.folio.settings.server.storage.SettingsStorage;
import org.folio.settings.server.storage.UserException;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.TenantInitHooks;

public class SettingsService implements RouterCreator, TenantInitHooks {

  public static final int BODY_LIMIT = 65536; // 64 kb

  private static final Logger log = LogManager.getLogger(SettingsService.class);

  @Override
  public Future<Router> createRouter(Vertx vertx) {
    return RouterBuilder.create(vertx, "openapi/settings.yaml")
        .map(routerBuilder -> {
          // https://vertx.io/docs/vertx-web/java/#_limiting_body_size
          routerBuilder.rootHandler(BodyHandler.create().setBodyLimit(BODY_LIMIT));
          handlers(routerBuilder);
          Router router = Router.router(vertx);
          router.put("/settings/upload")
              .handler(ctx -> UploadService.uploadEntries(ctx)
                  .onFailure(cause -> commonError(ctx, cause)));
          router.route("/*").subRouter(routerBuilder.createRouter());
          return router;
        });
  }

  private void failureHandler(RoutingContext ctx) {
    commonError(ctx, ctx.failure(), ctx.statusCode());
  }

  void commonError(RoutingContext ctx, Throwable cause) {
    commonError(ctx, cause, 500);
  }

  void commonError(RoutingContext ctx, Throwable cause, int defaultCode) {
    if (cause == null) {
      httpResponse(ctx, defaultCode, HttpResponseStatus.valueOf(defaultCode).reasonPhrase());
    } else if (cause instanceof ForbiddenException) {
      httpResponse(ctx, 403, cause.getMessage());
    } else if (cause instanceof NotFoundException) {
      httpResponse(ctx, 404, cause.getMessage());
    } else if (cause instanceof UserException) {
      httpResponse(ctx, 400, cause.getMessage());
    } else {
      httpResponse(ctx, defaultCode, cause);
    }
  }

  void httpResponse(RoutingContext ctx, int code, String message) {
    log.error("{} {} {}", ctx.request().method(), ctx.request().path(), message);
    HttpResponse.responseError(ctx, code, message);
  }

  void httpResponse(RoutingContext ctx, int code, Throwable cause) {
    log.error("{} {} {}", ctx.request().method(), ctx.request().path(), cause.getMessage(), cause);
    HttpResponse.responseError(ctx, code, cause.getMessage());
  }

  private void handlers(RouterBuilder routerBuilder) {
    route(routerBuilder, "getSettings", this::getSettings);
    route(routerBuilder, "postSetting", this::postSetting);
    route(routerBuilder, "getSetting", this::getSetting);
    route(routerBuilder, "putSetting", this::updateSetting);
    route(routerBuilder, "deleteSetting", this::deleteSetting);
  }

  private void route(RouterBuilder routerBuilder,
      String operationId, Function<RoutingContext, Future<Void>> function) {

    routerBuilder
        .operation(operationId)
        .handler(ctx -> function.apply(ctx)
            .onFailure(cause -> commonError(ctx, cause))
        )
        .failureHandler(this::failureHandler);
  }

  static SettingsStorage createFromParams(Vertx vertx, RequestParameters params) {
    // get tenant
    RequestParameter tenantParameter = params.headerParameter(XOkapiHeaders.TENANT);
    String tenant = tenantParameter.getString();

    // get user Id
    RequestParameter userIdParameter = params.headerParameter(XOkapiHeaders.USER_ID);
    UUID currentUserId = null;
    if (userIdParameter != null) {
      currentUserId = UUID.fromString(userIdParameter.getString());
    }

    // get permissions which is required in OpenAPI spec
    RequestParameter okapiPermissions = params.headerParameter(XOkapiHeaders.PERMISSIONS);
    JsonArray permissions = new JsonArray(okapiPermissions.getString());
    return new SettingsStorage(vertx, tenant, currentUserId, permissions);
  }

  static SettingsStorage create(RoutingContext ctx) {
    return createFromParams(ctx.vertx(), ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY));
  }

  static SettingsStorage create(Vertx vertx, HttpServerRequest params) {
    // get tenant
    String tenant = params.getHeader(XOkapiHeaders.TENANT);

    // get user Id
    String userIdParameter = params.getHeader(XOkapiHeaders.USER_ID);
    UUID currentUserId = null;
    if (userIdParameter != null) {
      currentUserId = UUID.fromString(userIdParameter);
    }
    // get permissions
    String okapiPermissions = params.getHeader(XOkapiHeaders.PERMISSIONS);
    if (okapiPermissions == null) {
      throw new UserException("Missing header " + XOkapiHeaders.PERMISSIONS);
    }
    JsonArray permissions = new JsonArray(okapiPermissions);
    return new SettingsStorage(vertx, tenant, currentUserId, permissions);
  }

  Future<Void> postSetting(RoutingContext ctx) {
    SettingsStorage storage = create(ctx);
    RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    RequestParameter body = params.body();
    Entry entry = body.getJsonObject().mapTo(Entry.class);
    return storage.createEntry(entry)
        .map(entity -> {
          ctx.response().setStatusCode(204);
          ctx.response().end();
          return null;
        });
  }

  Future<Void> getSetting(RoutingContext ctx) {
    SettingsStorage storage = create(ctx);
    RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    String id  = params.pathParameter("id").getString();
    return storage.getEntry(UUID.fromString(id))
        .map(entity -> {
          HttpResponse.responseJson(ctx, 200)
              .end(JsonObject.mapFrom(entity).encode());
          return null;
        });
  }

  Future<Void> updateSetting(RoutingContext ctx) {
    SettingsStorage storage = create(ctx);
    RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    RequestParameter body = params.body();
    Entry entry = body.getJsonObject().mapTo(Entry.class);
    UUID id  = UUID.fromString(params.pathParameter("id").getString());
    if (!id.equals(entry.getId())) {
      return Future.failedFuture(new UserException("id mismatch"));
    }
    return storage.updateEntry(entry)
        .map(entity -> {
          ctx.response().setStatusCode(204);
          ctx.response().end();
          return null;
        });
  }

  Future<Void> deleteSetting(RoutingContext ctx) {
    SettingsStorage configStorage = create(ctx);
    RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    String id  = params.pathParameter("id").getString();
    return configStorage.deleteEntry(UUID.fromString(id))
        .map(res -> {
          ctx.response().setStatusCode(204);
          ctx.response().end();
          return null;
        });
  }

  Future<Void> getSettings(RoutingContext ctx) {
    SettingsStorage storage = create(ctx);
    RequestParameters params = ctx.get(ValidationHandler.REQUEST_CONTEXT_KEY);
    RequestParameter queryParameter = params.queryParameter("query");
    String query = queryParameter != null ? queryParameter.getString() : null;
    RequestParameter limitParameter = params.queryParameter("limit");
    int limit = limitParameter != null ? limitParameter.getInteger() : 10;
    RequestParameter offsetParameter = params.queryParameter("offset");
    int offset = offsetParameter != null ? offsetParameter.getInteger() : 0;
    return storage.getEntries(ctx.response(), query, offset, limit);
  }

  @Override
  public Future<Void> postInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
    if (!tenantAttributes.containsKey("module_to")) {
      return Future.succeededFuture(); // doing nothing for disable
    }
    SettingsStorage storage = new SettingsStorage(vertx, tenant, null, null);
    return storage.init();
  }

  @Override
  public Future<Void> preInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
    return Future.succeededFuture();
  }
}
