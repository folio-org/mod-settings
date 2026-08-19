package org.folio.settings.server.storage;

import static java.net.HttpURLConnection.HTTP_OK;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.okapi.common.SemVer;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.settings.server.data.Entry;
import org.folio.tlib.TenantInitConf;
import org.folio.util.PercentCodec;

public class TagsStorage {

  private static final Logger log = LogManager.getLogger(TagsStorage.class);

  private static final SemVer SEM_VER_1_4_0 = new SemVer("1.4.0");

  static final String TAGS_SCOPE = "ui-tags.tags.manage";
  static final String TAGS_KEY = "tags_enabled";
  static final String TAGS_MODULE = "TAGS";

  private final Vertx vertx;
  private final String tenant;

  /**
   * Migrates the tags_enabled setting from mod-configuration for a tenant.
   */
  public TagsStorage(Vertx vertx, String tenant) {
    this.vertx = vertx;
    this.tenant = tenant;
  }

  /**
   * Migrate tags_enabled from mod-configuration into mod-settings, once.
   */
  public Future<Void> init(TenantInitConf tenantInitConf, String oldVersion) {
    var oldSemVersion = new SemVer(oldVersion);
    if (SEM_VER_1_4_0.compareTo(oldSemVersion) <= 0) {
      return Future.succeededFuture();
    }
    var webClient = WebClient.create(vertx);
    return getFromModConfiguration(tenantInitConf, webClient)
        .compose(this::migrateIfPresent)
        .onComplete(x -> webClient.close());
  }

  private Future<Boolean> getFromModConfiguration(
      TenantInitConf tenantInitConf, WebClient webClient) {

    var cql = "module==" + TAGS_MODULE + " AND configName==" + TAGS_KEY;
    return webClient.getAbs(uri(tenantInitConf, "/configurations/entries?query=", cql))
        .putHeader(XOkapiHeaders.TENANT, tenantInitConf.tenant())
        .putHeader(XOkapiHeaders.TOKEN, tenantInitConf.token())
        .send()
        .compose(httpResponse -> {
          int status = httpResponse.statusCode();
          if (status == 404) {
            return Future.succeededFuture(null);
          }
          if (status != HTTP_OK) {
            log.error("Failed to migrate tags_enabled setting for tenant {}: "
                + "unexpected status {} from mod-configuration", tenant, status);
            return Future.failedFuture("Failed to migrate tags_enabled setting: unexpected status "
                    + status + " from mod-configuration");
          }
          var configs = httpResponse.bodyAsJsonObject().getJsonArray("configs");
          if (configs == null || configs.isEmpty()) {
            return Future.succeededFuture(null);
          }
          var config = configs.getJsonObject(0);
          if (!TAGS_MODULE.equals(config.getString("module"))
              || !TAGS_KEY.equals(config.getString("configName"))) {
            // response doesn't match the query we sent (e.g. a test/proxy stand-in that
            // returns the same canned response for every request); treat as nothing to migrate
            log.warn("mod-configuration returned a config entry that doesn't match the "
                + "tags_enabled query for tenant {}; treating as nothing to migrate", tenant);
            return Future.succeededFuture(null);
          }
          var value = config.getString("value");
          try {
            return Future.succeededFuture(parseTagsEnabled(value));
          } catch (IllegalArgumentException e) {
            log.error("Failed to migrate tags_enabled setting for tenant {}: {}",
                tenant, e.getMessage());
            return Future.failedFuture("Failed to migrate tags_enabled setting: " + e.getMessage());
          }
        });
  }

  private Future<Void> migrateIfPresent(Boolean tagsEnabled) {
    if (tagsEnabled == null) {
      return Future.succeededFuture();
    }
    var entry = new Entry();
    entry.setId(UUID.randomUUID());
    entry.setScope(TAGS_SCOPE);
    entry.setKey(TAGS_KEY);
    entry.setValue("value", tagsEnabled);
    return new SettingsStorage(vertx, tenant, null, null).createEntryWoCheck(entry)
        .map(inserted -> {
          if (Boolean.TRUE.equals(inserted)) {
            log.info("Migrated tags_enabled={} setting for tenant {}", tagsEnabled, tenant);
          } else {
            log.info("tags_enabled setting already present in mod-settings for tenant {}, "
                + "skipping migration", tenant);
          }
          return null;
        });
  }

  static boolean parseTagsEnabled(String value) {
    if ("true".equalsIgnoreCase(value)) {
      return true;
    }
    if ("false".equalsIgnoreCase(value)) {
      return false;
    }
    throw new IllegalArgumentException("Cannot parse tags_enabled value: " + value);
  }

  private static String uri(TenantInitConf tenantInitConf, String path, String toEncode) {
    return tenantInitConf.okapiUrl() + path + PercentCodec.encode(toEncode);
  }
}
