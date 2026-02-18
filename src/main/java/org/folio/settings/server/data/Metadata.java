package org.folio.settings.server.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.OffsetDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Metadata(UUID updatedByUserId, OffsetDateTime updatedDate) {
}
