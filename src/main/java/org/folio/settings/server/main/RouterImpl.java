package org.folio.settings.server.main;

import static java.net.HttpURLConnection.HTTP_BAD_REQUEST;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.openapi.router.RouterBuilder;
import io.vertx.openapi.contract.OpenAPIContract;
import java.util.function.Function;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.HttpResponse;
import org.folio.settings.server.service.BaseUrlService;
import org.folio.settings.server.service.LocaleService;
import org.folio.settings.server.service.SettingsService;
import org.folio.settings.server.service.TenantAddressesService;
import org.folio.settings.server.service.UploadService;
import org.folio.settings.server.storage.ForbiddenException;
import org.folio.settings.server.storage.NotFoundException;
import org.folio.settings.server.storage.UserException;
import org.folio.tlib.RouterCreator;

public class RouterImpl implements RouterCreator {
  public static final int BODY_LIMIT = 65536; // 64 kb

  private static final Logger LOGGER = LogManager.getLogger(RouterImpl.class);

  @Override
  public Future<Router> createRouter(Vertx vertx) {
    return OpenAPIContract.from(vertx, "openapi/settings.yaml")
        .map(contract -> {
          var routerBuilder = RouterBuilder.create(vertx, contract);
          handlers(routerBuilder);
          var router = Router.router(vertx);
          router.route().failureHandler(this::failureHandler);
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
    commonError(ctx, cause, HTTP_INTERNAL_ERROR);
  }

  void commonError(RoutingContext ctx, Throwable cause, int defaultCode) {
    if (cause == null) {
      httpResponse(ctx, defaultCode, HttpResponseStatus.valueOf(defaultCode).reasonPhrase());
    } else if (cause instanceof ForbiddenException) {
      httpResponse(ctx, HTTP_FORBIDDEN, cause.getMessage());
    } else if (cause instanceof NotFoundException) {
      httpResponse(ctx, HTTP_NOT_FOUND, cause.getMessage());
    } else if (cause instanceof UserException) {
      httpResponse(ctx, HTTP_BAD_REQUEST, cause.getMessage());
    } else if (cause instanceof IllegalArgumentException) {
      httpResponse(ctx, HTTP_BAD_REQUEST, cause);
    } else {
      httpResponse(ctx, defaultCode, cause);
    }
  }

  void httpResponse(RoutingContext ctx, int code, String message) {
    if (LOGGER.isErrorEnabled()) {
      var request = ctx.request();
      LOGGER.error("{} {} {}", request.method(), request.path(), message);
    }
    HttpResponse.responseError(ctx, code, message);
  }

  void httpResponse(RoutingContext ctx, int code, Throwable cause) {
    if (LOGGER.isErrorEnabled()) {
      var request = ctx.request();
      LOGGER.error("{} {} {}", request.method(), request.path(), cause.getMessage(), cause);
    }
    HttpResponse.responseError(ctx, code, cause.getMessage());
  }

  private void handlers(RouterBuilder routerBuilder) {
    route(routerBuilder, "getBaseUrl", BaseUrlService::getBaseUrl);
    route(routerBuilder, "setBaseUrl", BaseUrlService::setBaseUrl);
    route(routerBuilder, "getLocale", LocaleService::getLocale);
    route(routerBuilder, "setLocale", LocaleService::setLocale);
    route(routerBuilder, "getTenantAddresses", TenantAddressesService::getTenantAddresses);
    route(routerBuilder, "createTenantAddress", TenantAddressesService::createTenantAddress);
    route(routerBuilder, "getTenantAddress", TenantAddressesService::getTenantAddress);
    route(routerBuilder, "updateTenantAddress", TenantAddressesService::updateTenantAddress);
    route(routerBuilder, "deleteTenantAddress", TenantAddressesService::deleteTenantAddress);
    route(routerBuilder, "getSettings", SettingsService::getSettings);
    route(routerBuilder, "postSetting", SettingsService::postSetting);
    route(routerBuilder, "getSetting", SettingsService::getSetting);
    route(routerBuilder, "putSetting", SettingsService::updateSetting);
    route(routerBuilder, "deleteSetting", SettingsService::deleteSetting);
  }

  private void route(RouterBuilder routerBuilder,
      String operationId, Function<RoutingContext, Future<Void>> function) {

    routerBuilder
        .getRoute(operationId)
        // disable automatic validation and body parsing and do it ourselves
        .setDoValidation(false)
        .addHandler(BodyHandler.create().setBodyLimit(BODY_LIMIT))
        .addHandler(ctx -> function.apply(ctx)
            .onFailure(cause -> commonError(ctx, cause)))
        .addFailureHandler(this::failureHandler);
  }
}
