package org.folio.settings.server.storage;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.Tuple;
import java.util.List;
import java.util.UUID;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.settings.server.data.Entry;
import org.folio.tlib.postgres.TenantPgPool;

public class SettingsStorage {

  private static final Logger log = LogManager.getLogger(SettingsStorage.class);

  private static final String CREATE_IF_NO_EXISTS = "CREATE TABLE IF NOT EXISTS ";

  private static final UUID GLOBAL_USER = UUID.fromString("b29326a-e58d-4566-b8d6-5edbffcb8cdf");

  private final TenantPgPool pool;

  private final String settingsTable;

  private final JsonArray permissions;

  private final UUID currentUser;


  /**
   * Construct storage request for a user with given okapi permissions.
   * @param vertx Vert.x handle
   * @param tenant tenant
   * @param currentUser UUID of user as it comes from X-Okapi-User-Id
   * @param permissions permissions as it comes from X-Okapi-Permissions
   */
  public SettingsStorage(Vertx vertx, String tenant, UUID currentUser, JsonArray permissions) {
    this.pool = TenantPgPool.pool(vertx, tenant);
    this.permissions = permissions;
    this.currentUser = currentUser;
    this.settingsTable = pool.getSchema() + ".settings";
  }

  /**
   * Prepares storage for a tenant, AKA tenant init.
   * @return async result
   */
  public Future<Void> init() {
    return pool.execute(List.of(
        CREATE_IF_NO_EXISTS + settingsTable
            + "(id uuid NOT NULL PRIMARY KEY,"
            + " scope VARCHAR NOT NULL,"
            + " key VARCHAR NOT NULL,"
            + " value JSONB NOT NULL,"
            + " userId uuid NOT NULL"
            + ")",
        "CREATE UNIQUE INDEX IF NOT EXISTS settings_scope_key_userid ON "
            + settingsTable + "(scope, key, userId)"
    ));
  }

  /**
   * Checks if access is allowed for configurations entry.
   * @param type read/write value
   * @param permissions permissions given at runtime
   * @param entry configurations entry that we check against
   * @param currentUser user as it is given at runtime
   * @return true if access is OK; false otherwise (forbidden)
   */
  static boolean checkDesiredPermissions(String type, JsonArray permissions, Entry entry,
      UUID currentUser) {
    UUID userId = entry.getUserId();
    if (userId == null) {
      return permissions.contains("settings.global." + type + "." + entry.getScope());
    }
    if (permissions.contains("settings.users." + type + "." + entry.getScope())) {
      return true;
    }
    return permissions.contains("settings.owner." + type + "." + entry.getScope())
        && currentUser != null && currentUser.equals(entry.getUserId());
  }

  Entry fromRow(Row row) {
    Entry entry = new Entry();
    entry.setId(row.getUUID("id"));
    entry.setScope(row.getString("scope"));
    entry.setKey(row.getString("key"));
    JsonObject value = row.getJsonObject("value");
    value.forEach(k -> entry.setValue(k.getKey(), k.getValue()));
    UUID userId = row.getUUID("userid");
    if (!userId.equals(GLOBAL_USER)) {
      entry.setUserId(userId);
    }
    return entry;
  }

  /**
   * Create configurations entry.
   * @param entry to be created
   * @return async result with success if created; failed otherwise
   */
  public Future<Void> createEntry(Entry entry) {
    if (!checkDesiredPermissions("write", permissions, entry, currentUser)) {
      return Future.failedFuture(new ForbiddenException());
    }
    return pool.preparedQuery(
            "INSERT INTO " + settingsTable
                + " (id, scope, key, value, userId)"
                + " VALUES ($1, $2, $3, $4, $5)"
                + " ON CONFLICT (scope, key, userId) DO NOTHING"
        )
        .execute(Tuple.of(entry.getId(), entry.getScope(),
            entry.getKey(), entry.getValue(),
            entry.getUserId() != null ? entry.getUserId() : GLOBAL_USER))
        .map(rowSet -> {
          if (rowSet.rowCount() == 0) {
            throw new UserException("Scope/Key/UserId constraint");
          }
          return null;
        });
  }

  /**
   * Get configurations entry.
   * @param id entry identifier
   * @return async result with entry value; failure otherwise
   */
  public Future<Entry> getEntry(UUID id) {
    return getEntryWoCheck(id)
        .map(entry -> {
          if (entry == null) {
            throw new NotFoundException();
          }
          if (!checkDesiredPermissions("read", permissions, entry, currentUser)) {
            throw new ForbiddenException();
          }
          return entry;
        });
  }

  Future<Entry> getEntryWoCheck(UUID id) {
    return pool.preparedQuery(
            "SELECT * FROM " + settingsTable + " WHERE id = $1")
        .execute(Tuple.of(id))
        .map(rowSet -> {
          RowIterator<Row> iterator = rowSet.iterator();
          if (!iterator.hasNext()) {
            return null;
          }
          return fromRow(iterator.next());
        });
  }

  /**
   * Delete configurations entry.
   * @param id entry identifier
   * @return async result; exception if not found or forbidden
   */
  public Future<Void> deleteEntry(UUID id) {
    return getEntryWoCheck(id).compose(entry -> {
      if (entry == null) {
        return Future.failedFuture(new NotFoundException());
      }
      if (!checkDesiredPermissions("write", permissions, entry, currentUser)) {
        return Future.failedFuture(new ForbiddenException());
      }
      return pool.preparedQuery(
              "DELETE FROM " + settingsTable + " WHERE id = $1")
          .execute(Tuple.of(id))
          .map(res -> {
            if (res.rowCount() == 0) {
              throw new NotFoundException();
            }
            return null;
          });
    });
  }

  /**
   * Update configurations entry.
   * @param entry to be created
   * @return async result with success if created; failed otherwise
   */
  public Future<Void> updateEntry(Entry entry) {
    if (!checkDesiredPermissions("write", permissions, entry, currentUser)) {
      return Future.failedFuture(new ForbiddenException());
    }
    return pool.preparedQuery(
            "UPDATE " + settingsTable
                + " SET scope = $2, key = $3, value = $4, userId = $5"
                + " WHERE id = $1"
        )
        .execute(Tuple.of(entry.getId(), entry.getScope(),
            entry.getKey(), entry.getValue(),
            entry.getUserId() != null ? entry.getUserId() : GLOBAL_USER))
        .map(rowSet -> {
          if (rowSet.rowCount() == 0) {
            throw new UserException("Scope/Key/UserId constraint");
          }
          return null;
        })
        .recover(e -> {
          if (e instanceof PgException pgException) {
            if (pgException.getMessage().contains("\"settings_scope_key_userid\" (23505)")) {
              return Future.failedFuture(new UserException("constraint problem"));
            }
          }
          return Future.failedFuture(e);
        })
        .mapEmpty();
  }

}
