package org.folio.settings.server.service;

import io.vertx.core.Future;
import io.vertx.core.Promise;
import io.vertx.core.http.HttpHeaders;
import io.vertx.core.json.JsonObject;
import io.vertx.core.parsetools.JsonEventType;
import io.vertx.core.parsetools.JsonParser;
import io.vertx.ext.web.RoutingContext;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.folio.okapi.common.HttpResponse;
import org.folio.settings.server.data.Entry;
import org.folio.settings.server.storage.SettingsStorage;
import org.folio.settings.server.storage.UserException;

public final class UploadService {

  private UploadService() { }

  /**
   * Upsert multiple settings.
   */
  public static Future<Void> uploadEntries(RoutingContext ctx) {
    try {
      String contentType = ctx.request().getHeader(HttpHeaders.CONTENT_TYPE);
      if (contentType == null || !contentType.startsWith("application/json")) {
        throw new UserException("Content-Type must be application/json");
      }
      SettingsStorage storage = SettingsService.create(ctx);
      JsonParser jsonParser = JsonParser.newParser(ctx.request());
      JsonObject uploadResponse = new JsonObject()
          .put("inserted", 0)
          .put("updated", 0);

      Promise<Void> promise = Promise.promise();
      AtomicInteger pending = new AtomicInteger();
      AtomicBoolean ended = new AtomicBoolean();
      jsonParser.handler(event -> {
        if (event.type().equals(JsonEventType.START_ARRAY)) {
          jsonParser.objectValueMode();
        } else if (event.type().equals(JsonEventType.END_ARRAY)) {
          jsonParser.objectEventMode();
        } else if (event.type().equals(JsonEventType.VALUE)) {
          JsonObject obj = event.objectValue();
          Entry entry = obj.mapTo(Entry.class);
          if (pending.incrementAndGet() >= 5) {
            jsonParser.pause();
          }
          storage.upsertEntry(entry)
              .map(b -> {
                String key = Boolean.TRUE.equals(b) ? "inserted" : "updated";
                uploadResponse.put(key, uploadResponse.getInteger(key) + 1);
                return null;
              })
              .onFailure(promise::tryFail)
              .onComplete(x -> {
                if (pending.decrementAndGet() <= 2) {
                  jsonParser.resume();
                }
                if (ended.get() && pending.get() == 0) {
                  promise.tryComplete();
                }
              });
        }
      });
      jsonParser.endHandler(x -> {
        ended.set(true);
        if (pending.get() == 0) {
          promise.tryComplete();
        }
      });
      // turn JSON parse errors to user errors : bad request body
      jsonParser.exceptionHandler(x -> promise.tryFail(new UserException(x.getMessage())));
      return promise.future().map(x -> {
        HttpResponse.responseJson(ctx, 200).end(uploadResponse.encode());
        return null;
      });
    } catch (Exception e) {
      return Future.failedFuture(e);
    }
  }
}
