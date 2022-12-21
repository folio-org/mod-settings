package org.folio.settings.server.storage;

public class ForbiddenException extends RuntimeException {
  public ForbiddenException() {
    super("Forbidden");
  }

}
