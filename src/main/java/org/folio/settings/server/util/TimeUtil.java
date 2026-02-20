package org.folio.settings.server.util;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

public class TimeUtil {

  private TimeUtil() {
  }

  /**
   * Create a JavaTimeModule configured to serialize date/time types
   * in ISO-8601 format with +00:00 instead of Z for UTC timezone.
   *
   * @return configured JavaTimeModule
   */
  public static JavaTimeModule createJavaTimeModule() {
    var module = new JavaTimeModule();
    module.addSerializer(OffsetDateTime.class, new JsonSerializer<>() {
      @Override
      public void serialize(OffsetDateTime value, JsonGenerator gen, SerializerProvider serializers)
          throws IOException {
        // Format manually to ensure +00:00 instead of Z
        var formatted = value.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        var offset = value.getOffset().equals(ZoneOffset.UTC) ? "+00:00" : value.getOffset().getId();
        gen.writeString(formatted + offset);
      }
    });
    return module;
  }

  /**
   * Get the current OffsetDateTime in UTC truncated to milliseconds precision.
   * This removes nanoseconds beyond milliseconds to ensure consistent
   * timestamp formatting across the application.
   *
   * @return current OffsetDateTime in UTC truncated to milliseconds
   */
  public static OffsetDateTime getTruncatedOffsetDateTime() {
    return OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
  }
}

