package org.folio.config.server.storage;

public class ForbiddenException extends RuntimeException {
    public ForbiddenException() {
    super("Forbidden");
  }

}
