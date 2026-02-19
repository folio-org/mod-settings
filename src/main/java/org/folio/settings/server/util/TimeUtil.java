package org.folio.settings.server.util;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoUnit;

public class TimeUtil {

  private TimeUtil() {
  }

  /**
   * Create a JavaTimeModule configured to serialize LocalDateTime
   * with UTC timezone offset (+00:00) appended.
   * This ensures consistency with other timestamp formats in the application.
   *
   * @return configured JavaTimeModule
   */
  public static JavaTimeModule createJavaTimeModule() {
    var module = new JavaTimeModule();
    var formatter = new DateTimeFormatterBuilder()
        .append(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        .appendLiteral("+00:00")
        .toFormatter();
    module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(formatter));
    return module;
  }

  /**
   * Get the current LocalDateTime in UTC truncated to milliseconds precision.
   * This removes nanoseconds beyond milliseconds to ensure consistent
   * timestamp formatting across the application.
   *
   * @return current LocalDateTime in UTC truncated to milliseconds
   */
  public static LocalDateTime getTruncatedOffsetDateTime() {
    return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MILLIS);
  }
}

