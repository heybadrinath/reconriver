package io.github.heybadrinath.reconriver.reconciliation;

import java.time.Instant;
import java.util.UUID;

public record RunView(
        UUID id,
        String status,
        int totalWorkItems,
        int processedItems,
        int matchedCount,
        int exceptionCount,
        double progressPercent,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Long durationMillis,
        boolean restartable,
        String failureMessage) {}
