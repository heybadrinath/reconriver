package io.github.heybadrinath.reconriver.ingestion;

import java.time.Instant;
import java.util.UUID;

public record ImportView(
        UUID id,
        SourceType sourceType,
        String fileName,
        String checksum,
        String status,
        int totalRows,
        int importedRows,
        int quarantinedRows,
        UUID duplicateOfId,
        String failureMessage,
        Instant createdAt,
        Instant updatedAt) {}
