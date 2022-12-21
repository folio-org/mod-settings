package org.folio.settings.server.storage;

public class NotFoundException extends RuntimeException {
  public NotFoundException() {
    super("Not Found");
  }
}
