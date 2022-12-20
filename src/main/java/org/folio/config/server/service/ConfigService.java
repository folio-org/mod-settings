package org.folio.config.server.service;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.RouterBuilder;
import org.folio.okapi.common.HttpResponse;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.TenantInitHooks;

public class ConfigService implements RouterCreator, TenantInitHooks {

  public static final int BODY_LIMIT = 65536; // 64 kb

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

  private void handlers(Vertx vertx, RouterBuilder routerBuilder) {
    routerBuilder
        .operation("getConfigurationEntries")
        .handler(ctx -> getConfigurationEntries(vertx, ctx)
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
        )
        .failureHandler(this::failureHandler);

    routerBuilder
        .operation("postConfigurationEntry")
        .handler(ctx -> postConfigurationEntry(vertx, ctx)
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
        )
        .failureHandler(this::failureHandler);

    routerBuilder
        .operation("getConfigurationEntry")
        .handler(ctx -> getConfigurationEntry(vertx, ctx)
            .onFailure(cause -> HttpResponse.responseError(ctx, 500, cause.getMessage()))
        )
        .failureHandler(this::failureHandler);
  }

  Future<Void> getConfigurationEntries(Vertx vertx, RoutingContext ctx) {
    System.out.println("getConfigurationEntries");
    return Future.failedFuture("Not implemented");
  }

  Future<Void> postConfigurationEntry(Vertx vertx, RoutingContext ctx) {
    System.out.println("postConfigurationEntry");
    return Future.failedFuture("Not implemented");
  }

  Future<Void> getConfigurationEntry(Vertx vertx, RoutingContext ctx) {
    System.out.println("getConfigurationEntry");
    return Future.failedFuture("Not implemented");
  }

  @Override
  public Future<Void> postInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
    return Future.succeededFuture();
  }

  @Override
  public Future<Void> preInit(Vertx vertx, String tenant, JsonObject tenantAttributes) {
    return Future.succeededFuture();
  }
}
