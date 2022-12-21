package org.folio.config.server.storage;

public class NotFoundException extends RuntimeException {
  public NotFoundException() {
    super("Not Found");
  }
}
