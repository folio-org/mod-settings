package org.folio.settings.server.data;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.LocalDateTime;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record Metadata(UUID createdByUserId, LocalDateTime createdDate,
                       UUID updatedByUserId, LocalDateTime updatedDate) {
}
