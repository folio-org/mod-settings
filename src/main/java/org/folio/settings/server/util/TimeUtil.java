package org.folio.settings.server.util;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.OffsetDateTimeSerializer;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;

public class TimeUtil {

  private TimeUtil() {
  }

  /**
   * Create a JavaTimeModule configured to serialize OffsetDateTime
   * with timezone offset format (+00:00) instead of Z format.
   * This ensures consistency with other timestamp formats in the application.
   *
   * @return configured JavaTimeModule
   */
  public static JavaTimeModule createJavaTimeModule() {
    var module = new JavaTimeModule();
    module.addSerializer(OffsetDateTime.class, new OffsetDateTimeSerializer(
        OffsetDateTimeSerializer.INSTANCE,
        false,
        new DateTimeFormatterBuilder()
            .append(java.time.format.DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .appendOffset("+HH:MM", "+00:00")
            .toFormatter(),
        null
    ));
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

