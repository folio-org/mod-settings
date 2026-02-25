package org.folio.settings.server.storage;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlConnection;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.folio.tlib.postgres.PgCqlDefinition;

public class Paginator {

  private static final Logger log = LogManager.getLogger(Paginator.class);

  private final String selectQuery;
  private final String countQuery;
  private final BiConsumer<HttpServerResponse, Row> rowMapper;

  /**
   * Pagination request parameters.
   */
  public record PaginationRequest(
      String query, Integer offset, Integer limit, PgCqlDefinition definition) {}

  /**
   * Create a paginator for the given table and request.
   */
  public Paginator(String tableName,
                   PaginationRequest request,
                   BiConsumer<HttpServerResponse, Row> rowMapper) {
    this.rowMapper = rowMapper;
    var cqlQuery = request.definition.parse(request.query);
    var where = cqlQuery.getWhereClause();
    var from = tableName + (where == null ? "" : " WHERE " + where);
    var orderBy = cqlQuery.getOrderByClause();
    this.selectQuery = "SELECT * FROM " + from
        + (orderBy == null ? "" : " ORDER BY " + orderBy)
        + " LIMIT " + request.limit + " OFFSET " + request.offset;
    this.countQuery = "SELECT COUNT(*) FROM " + from;
    log.debug("getTenantAddresses:: Select SQL query: {}", this.selectQuery);
    log.debug("getTenantAddresses:: Count SQL query: {}", this.countQuery);
  }

  /**
   * Stream paginated results to the HTTP response.
   */
  public Future<Void> streamResult(
      HttpServerResponse response, SqlConnection connection, String property) {
    var promise = Promise.<Void>promise();
    var sqlStreamFetchSize = 100;
    connection.prepare(selectQuery)
        .onFailure(promise::fail)
        .onSuccess(pq -> {
          response.setChunked(true);
          response.putHeader(HttpHeaders.CONTENT_TYPE, "application/json");
          response.write("{ \"" + property + "\" : [");
          var first = new AtomicBoolean(true);
          var stream = pq.createStream(sqlStreamFetchSize);
          stream.handler(row -> {
            if (!first.getAndSet(false)) {
              response.write(",");
            }
            rowMapper.accept(response, row);
          });
          stream.endHandler(end -> stream.close()
              .compose(x -> pq.close())
              .compose(x -> connection.query(countQuery).execute()
                  .map(rs -> rs.iterator().next().getInteger(0)))
              .onSuccess(totalRecords -> {
                resultFooter(response, totalRecords, null);
                promise.complete();
              })
              .onFailure(f -> {
                log.error("get total records error: {}", f.getMessage(), f);
                resultFooter(response, null, f.getMessage());
                promise.fail(f);
              }));
          stream.exceptionHandler(e -> {
            log.error("stream error: {}", e.getMessage(), e);
            resultFooter(response, null, e.getMessage());
            promise.fail(e);
          });
        });
    return promise.future();
  }

  private void resultFooter(HttpServerResponse response, Integer totalRecords, String diagnostic) {
    var resultInfo = new JsonObject();
    resultInfo.put("totalRecords", totalRecords);
    var diagnostics = new JsonArray();
    if (diagnostic != null) {
      diagnostics.add(new JsonObject().put("message", diagnostic));
    }
    resultInfo.put("diagnostics", diagnostics);
    response.write("], \"resultInfo\": " + resultInfo.encode() + "}");
    response.end();
  }
}
