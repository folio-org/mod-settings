package org.folio.settings.server.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class Metadata {

  private UUID updatedByUserId;
  private OffsetDateTime updatedDate;

  public Metadata(UUID updatedByUserId, OffsetDateTime updatedDate) {
    this.updatedByUserId = updatedByUserId;
    this.updatedDate = updatedDate;
  }

  public UUID getUpdatedByUserId() {
    return updatedByUserId;
  }

  public OffsetDateTime getUpdatedDate() {
    return updatedDate;
  }
}
