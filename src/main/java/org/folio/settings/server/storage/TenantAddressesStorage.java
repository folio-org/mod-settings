package org.folio.settings.server.storage;

import static java.net.HttpURLConnection.HTTP_OK;
import static org.folio.settings.server.util.StringUtil.isBlank;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.client.WebClient;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.folio.okapi.common.SemVer;
import org.folio.okapi.common.XOkapiHeaders;
import org.folio.settings.server.data.TenantAddress;
import org.folio.settings.server.data.TenantAddresses;
import org.folio.tlib.TenantInitConf;
import org.folio.tlib.postgres.TenantPgPool;
import org.folio.util.PercentCodec;

public class TenantAddressesStorage {

  private static final SemVer SEM_VER_1_3_0 = new SemVer("1.3.0");

  private final TenantPgPool pool;

  private final String addressesTable;

  /**
   * Database storage for tenant addresses for a tenant.
   */
  public TenantAddressesStorage(Vertx vertx, String tenant) {
    this.pool = TenantPgPool.pool(vertx, tenant);
    this.addressesTable = pool.getSchema() + ".tenant_addresses";
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
          (id uuid PRIMARY KEY,
           name text UNIQUE NOT NULL,
           address text NOT NULL)
        """.formatted(addressesTable)
    ));
  }

  private Future<Void> migrateData(TenantInitConf tenantInitConf, String oldVersion) {
    var oldSemVersion = new SemVer(oldVersion);
    if (SEM_VER_1_3_0.compareTo(oldSemVersion) <= 0) {
      return Future.succeededFuture();
    }

    var webClient = WebClient.create(tenantInitConf.vertx());
    return getAndDeleteFromModConfiguration(tenantInitConf, webClient)
        .compose(this::insertMigratedAddresses)
        .onComplete(x -> webClient.close())
        .mapEmpty();
  }

  private static Future<List<TenantAddress>> getAndDeleteFromModConfiguration(
      TenantInitConf tenantInitConf, WebClient webClient) {

    var cql = "module==TENANT AND configName==tenant.addresses";
    return webClient.getAbs(uri(tenantInitConf, "/configurations/entries?query=", cql))
        .putHeader(XOkapiHeaders.TENANT, tenantInitConf.tenant())
        .putHeader(XOkapiHeaders.TOKEN, tenantInitConf.token())
        .send()
        .compose(httpResponse -> {
          if (httpResponse.statusCode() != HTTP_OK) {
            return Future.succeededFuture();  // ignore GET failure
          }
          var configs = httpResponse.bodyAsJsonObject().getJsonArray("configs");
          if (configs == null || configs.isEmpty()) {
            return Future.succeededFuture();
          }

          List<TenantAddress> addresses = new ArrayList<>();
          List<Future<Void>> deletions = new ArrayList<>();
          for (int i = 0; i < configs.size(); i++) {
            var config = configs.getJsonObject(i);
            var id = config.getString("id");
            var value = config.getString("value");
            var parsed = parseAddress(id, value);
            if (parsed != null) {
              addresses.add(parsed);
            }

            if (isBlank(id)) {
              return Future.succeededFuture();
            }
            deletions.add(webClient.deleteAbs(uri(tenantInitConf, "/configurations/entries/", id))
                .putHeader(XOkapiHeaders.TENANT, tenantInitConf.tenant())
                .putHeader(XOkapiHeaders.TOKEN, tenantInitConf.token())
                .send()
                .otherwiseEmpty()
                .mapEmpty());
          }

          return Future.all(deletions)
              .otherwiseEmpty()
              .map(addresses);
        })
        .otherwiseEmpty();
  }

  private static TenantAddress parseAddress(String id, String value) {
    try {
      var tenantAddress = new JsonObject(value).mapTo(TenantAddress.class);
      return isBlank(tenantAddress.getName())
          ? null
          : updateTenantAddressIdIfNeeded(tenantAddress, id);
    } catch (Exception e) {
      return null;
    }
  }

  private static String uri(TenantInitConf tenantInitConf, String path, String toEncode) {
    return tenantInitConf.okapiUrl() + path + PercentCodec.encode(toEncode);
  }

  private Future<Void> insertMigratedAddresses(List<TenantAddress> addresses) {
    return addresses == null || addresses.isEmpty()
        ? Future.succeededFuture()
        : Future.all(addresses.stream().map(this::insertMigratedAddress).toList()).mapEmpty();
  }

  private Future<Void> insertMigratedAddress(TenantAddress address) {
    return pool.preparedQuery(("INSERT INTO %s (id, name, address) VALUES ($1, $2, $3) "
            + "ON CONFLICT (name) DO NOTHING").formatted(addressesTable))
        .execute(Tuple.of(address.getId(), address.getName(), address.getAddress()))
        .mapEmpty();
  }

  /**
   * Get tenant addresses.
   */
  public Future<TenantAddresses> getTenantAddresses(int offset, int limit) {
    return pool.preparedQuery(("SELECT id, name, address FROM %s "
            + "ORDER BY name LIMIT $1 OFFSET $2").formatted(addressesTable))
        .execute(Tuple.of(limit, offset))
        .map(this::mapToTenantAddresses)
        .map(TenantAddresses::new);
  }

  /**
   * Get tenant address by id.
   */
  public Future<TenantAddress> getTenantAddress(String id) {
    return pool.preparedQuery("SELECT id, name, address FROM %s WHERE id = $1"
            .formatted(addressesTable))
        .execute(Tuple.of(UUID.fromString(id)))
        .compose(this::mapToTenantAddress);
  }

  /**
   * Create tenant address.
   */
  public Future<TenantAddress> createTenantAddress(TenantAddress address) {
    updateTenantAddressIdIfNeeded(address, address.getId());
    return pool.preparedQuery("INSERT INTO %s (id, name, address) VALUES ($1, $2, $3)"
            .formatted(addressesTable))
        .execute(Tuple.of(address.getId(), address.getName(), address.getAddress()))
        .map(address);
  }

  /**
   * Update tenant address.
   */
  public Future<Void> updateTenantAddress(String id, TenantAddress address) {
    return pool.preparedQuery("UPDATE %s SET name = $1, address = $2 WHERE id = $3"
            .formatted(addressesTable))
        .execute(Tuple.of(address.getName(), address.getAddress(), UUID.fromString(id)))
        .compose(this::validateRowCount);
  }

  /**
   * Delete tenant address.
   */
  public Future<Void> deleteTenantAddress(String id) {
    return pool.preparedQuery("DELETE FROM %s WHERE id = $1".formatted(addressesTable))
        .execute(Tuple.of(UUID.fromString(id)))
        .compose(this::validateRowCount);
  }

  private List<TenantAddress> mapToTenantAddresses(RowSet<Row> rowSet) {
    var addresses = new ArrayList<TenantAddress>();
    rowSet.forEach(row -> addresses.add(new TenantAddress(
        row.getUUID("id").toString(),
        row.getString("name"),
        row.getString("address"))));
    return addresses;
  }

  private Future<TenantAddress> mapToTenantAddress(RowSet<Row> rowSet) {
    return validateRowCount(rowSet).map(v -> mapToTenantAddresses(rowSet).getFirst());
  }

  private Future<Void> validateRowCount(RowSet<Row> rowSet) {
    return rowSet.rowCount() == 0
        ? Future.failedFuture(new NotFoundException())
        : Future.succeededFuture();
  }

  private static TenantAddress updateTenantAddressIdIfNeeded(TenantAddress address, String id) {
    try {
      address.setId(UUID.fromString(id).toString());
    } catch (Exception e) {
      address.setId(UUID.randomUUID().toString());
    }
    return address;
  }

}
