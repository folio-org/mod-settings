package org.folio.settings.server.storage;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.settings.server.util.StringUtil.isBlank;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.Json;
import io.vertx.ext.web.client.WebClient;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import org.folio.okapi.common.SemVer;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.settings.server.data.LocaleSettings;
import org.folio.settings.server.util.LocaleUtil;
import org.folio.tlib.TenantInitConf;
import org.folio.tlib.postgres.TenantPgPool;
import org.folio.util.PercentCodec;

public class LocaleStorage {

  private static final SemVer SEM_VER_1_3_0 = new SemVer("1.3.0");
  private static final SemVer SEM_VER_1_3_1 = new SemVer("1.3.1");

  private final TenantPgPool pool;

  private final String localeTable;

  /**
   * Database storage for locale settings for a tenant.
   */
  public LocaleStorage(Vertx vertx, String tenant) {
    this.pool = TenantPgPool.pool(vertx, tenant);
    this.localeTable = pool.getSchema() + ".locale";
  }

  /**
   * Create the database table for the tenant.
   */
  public Future<Void> init(TenantInitConf tenantInitConf, String oldVersion) {
    return initTable()
        .compose(x -> migrateData(tenantInitConf, oldVersion));
  }

  private Future<Void> initTable() {
    return pool.execute(List.of(
        """
        CREATE TABLE IF NOT EXISTS %s
          (id uuid NOT NULL,
           locale text NOT NULL,
           currency text NOT NULL,
           timezone text NOT NULL,
           numberingsystem text);
        """.formatted(localeTable),

        """
        DO $$
          BEGIN
            INSERT INTO %s
            VALUES ('d8e0939b-eee9-45e0-942f-b8354335724b',
                    'en-US',
                    'USD',
                    'UTC',
                    null)
            ON CONFLICT DO NOTHING;
          EXCEPTION WHEN SQLSTATE 'P0001' THEN NULL;
          END
        $$
        """.formatted(localeTable),

        """
        CREATE OR REPLACE FUNCTION %s.locale_singleton()
          RETURNS TRIGGER AS $$
          BEGIN
            IF (TG_OP = 'DELETE') THEN
              RAISE EXCEPTION 'Cannot delete locale settings record.';
            ELSIF (TG_OP = 'INSERT') THEN
              RAISE EXCEPTION 'Cannot insert second locale settings record.';
            END IF;
            RETURN NEW;
          END;
          $$ language plpgsql;
        """.formatted(pool.getSchema()),

        """
        CREATE OR REPLACE TRIGGER locale_singleton BEFORE DELETE OR INSERT ON %s
          EXECUTE FUNCTION %s.locale_singleton()
        """.formatted(localeTable, pool.getSchema())
        ));
  }

  private Future<Void> allowNullInNumberingSystem() {
    return pool.execute(List.of(
        """
        ALTER TABLE %s ALTER COLUMN numberingsystem DROP NOT NULL;
        """.formatted(localeTable)));
  }

  private Future<Void> migrateData(TenantInitConf tenantInitConf, String oldVersion) {
    var oldSemVersion = new SemVer(oldVersion);
    var future = Future.<Void>succeededFuture();

    if (oldSemVersion.compareTo(SEM_VER_1_3_0) < 0) {
      var webClient = WebClient.create(tenantInitConf.vertx());
      future = future.compose(x -> getAndDeleteFromModConfiguration(tenantInitConf, webClient))
          .compose(this::updateLocaleSanitized)
          .onComplete(x -> webClient.close());
    }

    if (oldSemVersion.compareTo(SEM_VER_1_3_1) < 0) {
      future = future.compose(x -> allowNullInNumberingSystem());
    }

    return future;
  }

  private static Future<LocaleSettings> getAndDeleteFromModConfiguration(
      TenantInitConf tenantInitConf, WebClient webClient) {

    var cql = "module==ORG AND configName==localeSettings NOT userId=\"\" NOT code=\"\"";
    return webClient.getAbs(uri(tenantInitConf, "/configurations/entries?query=", cql))
        .putHeader(XOkapiHeaders.TENANT, tenantInitConf.tenant())
        .putHeader(XOkapiHeaders.TOKEN, tenantInitConf.token())
        .send()
        .compose(httpResponse -> {
          if (httpResponse.statusCode() != HTTP_OK) {
            return Future.succeededFuture();  // ignore GET failure
          }
          var config = httpResponse.bodyAsJsonObject().getJsonArray("configs").getJsonObject(0);
          var id = config.getString("id");
          var value = config.getString("value");
          var localeSettings = Json.decodeValue(value, LocaleSettings.class);
          return webClient.deleteAbs(uri(tenantInitConf, "/configurations/entries/", id))
              .putHeader(XOkapiHeaders.TENANT, tenantInitConf.tenant())
              .putHeader(XOkapiHeaders.TOKEN, tenantInitConf.token())
              .send()
              .otherwiseEmpty()  // ignore DELETE failure
              .map(localeSettings);
        })
        .otherwiseEmpty();
  }

  private static String uri(TenantInitConf tenantInitConf, String path, String toEncode) {
    return tenantInitConf.okapiUrl() + path + PercentCodec.encode(toEncode);
  }

  /**
   * Get locale settings.
   */
  public Future<LocaleSettings> getLocale() {
    return pool.query("SELECT locale, currency, timezone, numberingsystem FROM " + localeTable)
        .execute()
        .map(rowSet -> {
          var row = rowSet.iterator().next();
          return new LocaleSettings(
              row.getString("locale"),
              row.getString("currency"),
              row.getString("timezone"),
              row.getString("numberingsystem"));
        });
  }

  /**
   * Update locale settings.
   */
  public Future<Void> updateLocale(LocaleSettings localeSettings) {
    return pool.preparedQuery("UPDATE " + localeTable
        + " SET locale = $1, currency = $2, timezone = $3, numberingsystem = $4")
        .execute(Tuple.of(
            localeSettings.getLocale(),
            localeSettings.getCurrency(),
            localeSettings.getTimezone(),
            localeSettings.getNumberingSystem()))
        .mapEmpty();
  }

  private Future<Void> updateLocaleSanitized(LocaleSettings localeSettings) {
    if (localeSettings == null) {
      return Future.succeededFuture();
    }
    if (isBlank(localeSettings.getLocale())) {
      localeSettings.setLocale("en-US");
    }
    if (isBlank(localeSettings.getTimezone())) {
      localeSettings.setTimezone("UTC");
    }
    if (isBlank(localeSettings.getCurrency())) {
      localeSettings.setCurrency("USD");
    }
    if (!LocaleUtil.isValidNumberingSystem(localeSettings.getNumberingSystem())) {
      localeSettings.setNumberingSystem(null);
    }
    return updateLocale(localeSettings);
  }
}
