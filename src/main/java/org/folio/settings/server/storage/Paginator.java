package org.folio.settings.server.storage;

import io.vertx.core.Future;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.sqlclient.Row;
import io.vertx.sqlclient.SqlConnection;
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
    log.debug("Paginator:: Select SQL query: {}", this.selectQuery);
    log.debug("Paginator:: Count SQL query: {}", this.countQuery);
  }

  /**
   * Stream paginated results to the HTTP response.
   */
  public Future<Void> streamResult(
      HttpServerResponse response, SqlConnection connection, String property) {
    return StreamHelper.streamResult(
        response, connection, selectQuery, countQuery, property, rowMapper);
  }
}