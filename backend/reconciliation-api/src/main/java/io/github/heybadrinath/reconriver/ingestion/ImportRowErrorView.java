package io.github.heybadrinath.reconriver.ingestion;

import java.time.Instant;
import java.util.UUID;

public record ImportRowErrorView(
        UUID id, int rowNumber, String errorCode, String message, Instant createdAt) {}
