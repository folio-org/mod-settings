package org.folio.settings.server.main;

import io.vertx.core.Future;
import io.vertx.core.VerticleBase;
import io.vertx.core.http.HttpServerOptions;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.Config;
import org.folio.okapi.common.ModuleVersionReporter;
import org.folio.settings.server.service.TenantService;
import org.folio.tlib.RouterCreator;
import org.folio.tlib.api.HealthApi;
import org.folio.tlib.api.Tenant2Api;
import org.folio.tlib.postgres.TenantPgPool;

public class MainVerticle extends VerticleBase {
  private static final Logger log = LogManager.getLogger(MainVerticle.class);

  @Override
  public Future<?> start() {
    TenantPgPool.setModule("mod-settings");
    ModuleVersionReporter m = new ModuleVersionReporter("org.folio/mod-settings");
    log.info("Starting {} {} {}", m.getModule(), m.getVersion(), m.getCommitId());

    final int port = Integer.parseInt(
        Config.getSysConf("http.port", "port", "8081", config()));
    log.info("Listening on port {}", port);

    RouterCreator[] routerCreators = {
        new RouterImpl(),
        new Tenant2Api(new TenantService()),
        new HealthApi(),
    };

    var httpServerOptions = new HttpServerOptions()
        .setCompressionSupported(true)
        .setDecompressionSupported(true)
        .setHandle100ContinueAutomatically(true);
    return RouterCreator.mountAll(vertx, routerCreators, "mod-settings")
        .compose(router -> vertx.createHttpServer(httpServerOptions)
            .requestHandler(router)
            .listen(port));
  }

  @Override
  public Future<?> stop() {
    return TenantPgPool.closeAll();
  }
}
