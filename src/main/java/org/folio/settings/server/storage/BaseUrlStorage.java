package org.folio.settings.server.storage;

import static java.net.HttpURLConnection.HTTP_OK;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.ext.web.client.WebClient;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import org.folio.okapi.common.SemVer;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.tlib.TenantInitConf;
import org.folio.tlib.postgres.TenantPgPool;
import org.folio.util.PercentCodec;

public class BaseUrlStorage {

  private static final SemVer SEM_VER_1_3_0 = new SemVer("1.3.0");

  private final TenantPgPool pool;

  private final String table;

  /**
   * Database storage for baseUrl.
   */
  public BaseUrlStorage(Vertx vertx, String tenant) {
    this.pool = TenantPgPool.pool(vertx, tenant);
    this.table = pool.getSchema() + ".baseurl";
  }

  /**
   * Create database table for tenant.
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
           baseurl text NOT NULL)
        """.formatted(table),

        """
        DO $$
          BEGIN
            INSERT INTO %s
            VALUES ('fb8f6019-af40-4654-9b91-58c9d1b80151',
                    'http://localhost:3000')
            ON CONFLICT DO NOTHING;
          EXCEPTION WHEN SQLSTATE 'P0001' THEN NULL;
          END
        $$
        """.formatted(table),

        """
        CREATE OR REPLACE FUNCTION %s_singleton()
          RETURNS TRIGGER AS $$
          BEGIN
            IF (TG_OP = 'DELETE') THEN
              RAISE EXCEPTION 'Cannot delete baseurl record.';
            ELSIF (TG_OP = 'INSERT') THEN
              RAISE EXCEPTION 'Cannot insert second baseurl record.';
            END IF;
            RETURN NEW;
          END;
          $$ language plpgsql
        """.formatted(table),

        """
        CREATE OR REPLACE TRIGGER baseurl_singleton BEFORE DELETE OR INSERT ON %s
          EXECUTE FUNCTION %s_singleton()
        """.formatted(table, table)
        ));
  }

  private Future<Void> migrateData(TenantInitConf tenantInitConf, String oldVersion) {
    var oldSemVersion = new SemVer(oldVersion);
    if (SEM_VER_1_3_0.compareTo(oldSemVersion) <= 0) {
      return Future.succeededFuture();
    }

    var webClient = WebClient.create(tenantInitConf.vertx());
    return getAndDeleteFromModConfiguration(tenantInitConf, webClient)
        .compose(baseUrl -> baseUrl == null ? Future.succeededFuture() : updateBaseUrl(baseUrl))
        .onComplete(x -> webClient.close())
        .mapEmpty();
  }

  private static Future<String> getAndDeleteFromModConfiguration(
      TenantInitConf tenantInitConf, WebClient webClient) {

    var cql = "module==USERSBL AND configName==FOLIO_HOST";
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
          var baseUrl = stripTrailingSlashes(config.getString("value"));

          return webClient.deleteAbs(uri(tenantInitConf, "/configurations/entries/", id))
              .putHeader(XOkapiHeaders.TENANT, tenantInitConf.tenant())
              .putHeader(XOkapiHeaders.TOKEN, tenantInitConf.token())
              .send()
              .otherwiseEmpty()  // ignore DELETE failure
              .map(baseUrl);
        })
        .otherwiseEmpty();
  }

  private static String uri(TenantInitConf tenantInitConf, String path, String toEncode) {
    return tenantInitConf.okapiUrl() + path + PercentCodec.encode(toEncode);
  }

  static String stripTrailingSlashes(String url) {
    var end = url.length();
    while (end > 0 && url.charAt(end - 1) == '/') {
      end--;
    }
    return url.substring(0, end);
  }

  /**
   * Get baseUrl.
   */
  public Future<String> getBaseUrl() {
    return pool.query("SELECT baseurl FROM " + table)
        .execute()
        .map(rowSet -> rowSet.iterator().next().getString("baseurl"));
  }

  /**
   * Update baseUrl.
   */
  public Future<Void> updateBaseUrl(String baseUrl) {
    return pool.preparedQuery("UPDATE " + table + " SET baseurl = $1")
        .execute(Tuple.of(baseUrl))
        .mapEmpty();
  }
}
