package org.folio.config.server.storage;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.config.server.data.Entry;
import org.folio.tlib.postgres.TenantPgPool;

public class ConfigStorage {

  private static final Logger log = LogManager.getLogger(ConfigStorage.class);

  private static final String CREATE_IF_NO_EXISTS = "CREATE TABLE IF NOT EXISTS ";

  private final TenantPgPool pool;

  private final String configTable;

  private final JsonArray permissions;

  private final UUID currentUser;

  public ConfigStorage(Vertx vertx, String tenant, UUID currentUser, JsonArray permissions) {
    this.pool = TenantPgPool.pool(vertx, tenant);
    this.permissions = permissions;
    this.currentUser = currentUser;
    this.configTable = pool.getSchema() + ".config";
  }


  public Future<Void> init() {
    return pool.execute(List.of(
        CREATE_IF_NO_EXISTS + configTable
            + "(id uuid NOT NULL PRIMARY KEY,"
            + " scope VARCHAR NOT NULL,"
            + " key VARCHAR NOT NULL,"
            + " value JSONB NOT NULL,"
            + " userId uuid"
            + ")",
        "CREATE UNIQUE INDEX IF NOT EXISTS config_scope_key ON "
            + configTable + "(scope, key, userId)"
        ));
  }

  static boolean checkDesiredPermissions(String type, JsonArray permissions, Entry entry, UUID currentUser) {
    UUID userId = entry.getUserId();
    if (userId == null) {
      return permissions.contains("config.global." + type + "." + entry.getScope());
    }
    if (permissions.contains("config.others." + type + "." + entry.getScope())) {
      return true;
    }
    return permissions.contains("config.user." + type + "." + entry.getScope()) &&
          currentUser != null && currentUser.equals(entry.getUserId());
  }

  public Future<Void> createEntry(Entry entry) {
    if (!checkDesiredPermissions("write", permissions, entry, currentUser)) {
      return Future.failedFuture(new ForbiddenException());
    }
    return pool.preparedQuery(
            "INSERT INTO " + configTable
                + " (id, scope, key, value, userId)"
                + " VALUES ($1, $2, $3, $4, $5)")
        .execute(Tuple.of(entry.getId(), entry.getScope(),
            entry.getKey(), entry.getValue(), entry.getUserId()))
        .mapEmpty();
  }

  Entry fromRow(Row row) {
    Entry entry = new Entry();
    entry.setId(row.getUUID("id"));
    entry.setScope(row.getString("scope"));
    entry.setKey(row.getString("key"));
    JsonObject value = row.getJsonObject("value");
    value.forEach(k -> entry.setValue(k.getKey(), k.getValue()));
    entry.setUserId(row.getUUID("userid"));
    return entry;
  }

  public Future<Entry> getEntry(UUID id) {
    return pool.preparedQuery(
            "SELECT * FROM " + configTable + " WHERE id = $1")
        .execute(Tuple.of(id))
        .map(rowSet -> {
          RowIterator<Row> iterator = rowSet.iterator();
          if (!iterator.hasNext()) {
            return null;
          }
          Entry entry = fromRow(iterator.next());
          if (!checkDesiredPermissions("read", permissions, entry, currentUser)) {
            throw new ForbiddenException();
          }
          return entry;
        });
  }

}
