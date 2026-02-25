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

/**
 * Utility for streaming SQL query results to an HTTP response as JSON.
 */
public final class StreamHelper {

  private static final Logger log = LogManager.getLogger(StreamHelper.class);

  private StreamHelper() { }

  /**
   * Stream rows from a SQL query to the HTTP response as a JSON array.
   *
   * @param response   HTTP response to write to
   * @param connection SQL connection to use
   * @param selectQuery SQL SELECT query
   * @param countQuery  SQL COUNT query for totalRecords
   * @param property   JSON property name for the array (e.g. "items", "addresses")
   * @param rowMapper  maps each row to the response
   */
  public static Future<Void> streamResult(
      HttpServerResponse response, SqlConnection connection,
      String selectQuery, String countQuery,
      String property, BiConsumer<HttpServerResponse, Row> rowMapper) {
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

  private static void resultFooter(
      HttpServerResponse response, Integer totalRecords, String diagnostic) {
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