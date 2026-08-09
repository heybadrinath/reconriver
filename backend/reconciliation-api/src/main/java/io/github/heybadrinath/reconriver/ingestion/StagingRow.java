package io.github.heybadrinath.reconriver.ingestion;

import java.util.UUID;

record StagingRow(
        UUID id,
        UUID importFileId,
        UUID workspaceId,
        SourceType sourceType,
        int rowNumber,
        String rawJson) {}
