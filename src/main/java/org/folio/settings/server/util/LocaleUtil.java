package org.folio.settings.server.util;

public final class LocaleUtil {
  /**
   * Is "latn", "arab", or null.
   */
  public static boolean isValidNumberingSystem(String s) {
    return switch (s) {
      case null -> true;
      case "latn", "arab" -> true;
      default -> false;
    };
  }
}
