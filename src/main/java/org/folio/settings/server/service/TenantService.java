package org.folio.settings.server.service;

import io.vertx.core.Future;
import org.folio.settings.server.storage.LocaleStorage;
import org.folio.settings.server.storage.SettingsStorage;
import org.folio.settings.server.storage.VersionStorage;
import org.folio.tlib.TenantInitConf;
import org.folio.tlib.TenantInitHooks;

public class TenantService implements TenantInitHooks {

  @Override
  public Future<Void> postInit(TenantInitConf tenantInitConf) {
    if (!tenantInitConf.tenantAttributes().containsKey("module_to")) {
      return Future.succeededFuture(); // doing nothing for disable
    }
    var vertx = tenantInitConf.vertx();
    var tenant = tenantInitConf.tenant();
    var versionStorage = new VersionStorage(vertx, tenant);
    return versionStorage.init()
        .compose(x -> versionStorage.getVersion())
        .compose(version -> new LocaleStorage(vertx, tenant).init(tenantInitConf, version))
        .compose(x -> new SettingsStorage(vertx, tenant, null, null).init())
        .compose(x -> versionStorage.setVersion(tenantInitConf.moduleTo().toString()));
  }

}
