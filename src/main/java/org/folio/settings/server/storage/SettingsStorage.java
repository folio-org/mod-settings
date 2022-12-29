package org.folio.settings.server.storage;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.pgclient.PgException;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.RowIterator;
import io.vertx.sqlclient.RowSet;
import io.vertx.sqlclient.RowStream;
import io.vertx.sqlclient.SqlConnection;
import io.vertx.sqlclient.Tuple;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.settings.server.data.Entry;
import org.folio.tlib.postgres.PgCqlDefinition;
import org.folio.tlib.postgres.PgCqlQuery;
import org.folio.tlib.postgres.TenantPgPool;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldText;
import org.folio.tlib.postgres.cqlfield.PgCqlFieldUuid;

public class SettingsStorage {

  private static final Logger log = LogManager.getLogger(SettingsStorage.class);

  private static final String CREATE_IF_NO_EXISTS = "CREATE TABLE IF NOT EXISTS ";

  private final TenantPgPool pool;

  private final String settingsTable;

  private final JsonArray permissions;

  private final UUID currentUser;


  /**
   * Construct storage request for a user with given okapi permissions.
   *
   * @param vertx       Vert.x handle
   * @param tenant      tenant
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
   *
   * @return async result
   */
  public Future<Void> init() {
    return pool.execute(List.of(
        CREATE_IF_NO_EXISTS + settingsTable
            + "(id uuid NOT NULL PRIMARY KEY,"
            + " scope VARCHAR NOT NULL,"
            + " key VARCHAR NOT NULL,"
            + " value JSONB NOT NULL,"
            + " userId uuid"
            + ")",
        "CREATE UNIQUE INDEX IF NOT EXISTS settings_scope_key_users ON "
            + settingsTable + "(scope, key, userId) WHERE userId is NOT NULL",
        "CREATE UNIQUE INDEX IF NOT EXISTS settings_scope_key_global ON "
            + settingsTable + "(scope, key) WHERE userId is NULL"
    ));
  }

  /**
   * Checks if access is allowed for setting.
   *
   * @param type        read/write value
   * @param permissions permissions given at runtime
   * @param entry       setting that we check against
   * @param currentUser user as it is given at runtime
   * @return true if access is OK; false otherwise (forbidden)
   */
  static boolean checkDesiredPermissions(String type, JsonArray permissions,
      Entry entry, UUID currentUser) {
    UUID userId = entry.getUserId();
    if (userId == null) {
      return permissions.contains("settings.global." + type + "." + entry.getScope());
    }
    if (permissions.contains("settings.users." + type + "." + entry.getScope())) {
      return true;
    }
    return permissions.contains("settings.owner." + type + "." + entry.getScope())
        && currentUser != null && currentUser.equals(userId);
  }

  static List<String> getCqlLimitPermissions(
      JsonArray permissions, UUID currentUser) {
    Map<String, Set<String>> scopeMap = new HashMap<>();
    permissions.forEach(p -> {
      if (p instanceof String str) {
        String[] split = str.split("\\.");
        if (split.length == 4 && split[0].equals("settings")
            && "read".equals(split[2])) {
          String scope = split[3];
          scopeMap.putIfAbsent(scope, new TreeSet<>());
          Set<String> rights = scopeMap.get(scope);
          rights.add(split[1]);
        }
      }
    });
    List<String> queryLimits = new ArrayList<>();
    scopeMap.forEach((scope,rights) -> {
      String scopeEq = "scope = \"" + scope + "\"";
      if (rights.contains("global")) {
        if (rights.contains("users")) {
          queryLimits.add(scopeEq);
        } else if (rights.contains("owner") && currentUser != null) {
          queryLimits.add("(" + scopeEq
              + " and (userId <> \"\" or userId = \"" + currentUser + "\"))");
        } else {
          queryLimits.add("(" + scopeEq + " and userId <> \"\")");
        }
      } else {
        if (rights.contains("users")) {
          queryLimits.add("(" + scopeEq + " and userId = \"\")");
        } else if (rights.contains("owner") && currentUser != null) {
          queryLimits.add("(" + scopeEq + " and userId = \"" + currentUser + "\")");
        }
      }
    });
    return queryLimits;
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

  static String getOnConflictClause(Entry entry) {
    return entry.getUserId() != null
        ? "ON CONFLICT (scope, key, userId) WHERE userId is NOT NULL"
        : "ON CONFLICT (scope, key) WHERE userId is NULL";
  }

  /**
   * Create configurations entry.
   *
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
                + getOnConflictClause(entry) + " DO NOTHING"
        )
        .execute(Tuple.of(entry.getId(), entry.getScope(),
            entry.getKey(), entry.getValue(),
            entry.getUserId()))
        .map(rowSet -> {
          if (rowSet.rowCount() == 0) {
            throw new ForbiddenException();
          }
          return null;
        });
  }

  /**
   * Get configurations entry.
   *
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
            throw new NotFoundException();
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
   *
   * @param id entry identifier
   * @return async result; exception if not found or forbidden
   */
  public Future<Void> deleteEntry(UUID id) {
    return getEntryWoCheck(id).compose(entry -> {
      if (entry == null) {
        return Future.failedFuture(new NotFoundException());
      }
      if (!checkDesiredPermissions("write", permissions, entry, currentUser)) {
        return Future.failedFuture(new NotFoundException());
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
   *
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
            entry.getUserId()))
        .map(rowSet -> {
          if (rowSet.rowCount() == 0) {
            throw new NotFoundException();
          }
          return null;
        })
        .recover(e -> {
          if (e instanceof PgException pgException
              && pgException.getMessage().contains("(23505)")) {
            return Future.failedFuture(new NotFoundException());

          }
          return Future.failedFuture(e);
        })
        .mapEmpty();
  }

  /**
   * Upsert settings entry.
   * @param entry new entry or entry with new value
   * @return async result with true if inserted; false if updated
   */
  public Future<Boolean> upsertEntry(Entry entry) {
    if (entry.getId() != null) {
      return Future.failedFuture(new UserException("No id must supplied for upload"));
    }
    entry.setId(UUID.randomUUID());
    if (!checkDesiredPermissions("write", permissions, entry, currentUser)) {
      return Future.failedFuture(new ForbiddenException());
    }
    return pool.preparedQuery(
            "INSERT INTO " + settingsTable
                + "(id, scope, key, value, userId)"
                + " VALUES ($1, $2, $3, $4, $5)"
              + getOnConflictClause(entry) + " DO UPDATE SET value = $4"
              + " RETURNING id"
        )
        .execute(Tuple.of(entry.getId(), entry.getScope(),
            entry.getKey(), entry.getValue(),
            entry.getUserId()))
        .map(rowSet -> rowSet.iterator().next().getUUID("id").equals(entry.getId()));
  }

  /**
   * Get entries with optional cqlQuery.
   *
   * @param response HTTP response for result
   * @param cqlQuery  CQL cqlQuery; null if no cqlQuery is provided
   * @param offset starting offset of entries returned
   * @param limit  maximum number of entries returned
   * @return async result
   */
  public Future<Void> getEntries(HttpServerResponse response, String cqlQuery,
      int offset, int limit) {
    List<String> queryLimits = getCqlLimitPermissions(permissions, currentUser);
    if (queryLimits.isEmpty()) {
      return Future.failedFuture(new ForbiddenException());
    }
    String joinedCql = String.join(" or ", queryLimits);

    PgCqlDefinition definition = PgCqlDefinition.create();
    definition.addField("id", new PgCqlFieldUuid());
    definition.addField("scope", new PgCqlFieldText());
    definition.addField("key", new PgCqlFieldText());
    definition.addField("userId", new PgCqlFieldUuid());

    PgCqlQuery pgCqlQuery = definition.parse(cqlQuery, joinedCql);
    String sqlOrderBy = pgCqlQuery.getOrderByClause();
    String from = settingsTable + " WHERE" + pgCqlQuery.getWhereClause();
    String sqlQuery = "SELECT * FROM " + from
        + (sqlOrderBy == null ? "" : " ORDER BY " + sqlOrderBy)
        + " LIMIT " + limit + " OFFSET " + offset;

    log.debug("SQL: {}", sqlQuery);
    String countQuery = "SELECT COUNT(*) FROM " + from;
    return pool.getConnection()
        .compose(connection ->
            streamResult(response, connection, sqlQuery, countQuery)
                .onFailure(x -> connection.close())
        );
  }

  Future<Void> streamResult(HttpServerResponse response,
      SqlConnection connection, String query, String cnt) {

    String property = "items";
    Tuple tuple = Tuple.tuple();
    int sqlStreamFetchSize = 100;

    return connection.prepare(query)
        .compose(pq ->
            connection.begin().map(tx -> {
              response.setChunked(true);
              response.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
              response.write("{ \"" + property + "\" : [");
              AtomicBoolean first = new AtomicBoolean(true);
              RowStream<Row> stream = pq.createStream(sqlStreamFetchSize, tuple);
              stream.handler(row -> {
                if (!first.getAndSet(false)) {
                  response.write(",");
                }
                Entry entry = fromRow(row);
                response.write(JsonObject.mapFrom(entry).encode());
              });
              stream.endHandler(end -> {
                Future<RowSet<Row>> cntFuture = cnt != null
                    ? connection.preparedQuery(cnt).execute(tuple)
                    : Future.succeededFuture(null);
                cntFuture
                    .onSuccess(cntRes -> resultFooter(response, cntRes, null))
                    .onFailure(f -> {
                      log.error(f.getMessage(), f);
                      resultFooter(response,null, f.getMessage());
                    })
                    .eventually(x -> tx.commit().compose(y -> connection.close()));
              });
              stream.exceptionHandler(e -> {
                log.error("stream error {}", e.getMessage(), e);
                resultFooter(response, null, e.getMessage());
                tx.commit().compose(y -> connection.close());
              });
              return null;
            })
        );
  }

  void resultFooter(HttpServerResponse response, RowSet<Row> rowSet, String diagnostic) {
    JsonObject resultInfo = new JsonObject();
    if (rowSet != null) {
      int pos = 0;
      Row row = rowSet.iterator().next();
      int count = row.getInteger(pos);
      resultInfo.put("totalRecords", count);
    }
    JsonArray diagnostics = new JsonArray();
    if (diagnostic != null) {
      diagnostics.add(new JsonObject().put("message", diagnostic));
    }
    resultInfo.put("diagnostics", diagnostics);
    response.write("], \"resultInfo\": " + resultInfo.encode() + "}");
    response.end();
  }

}