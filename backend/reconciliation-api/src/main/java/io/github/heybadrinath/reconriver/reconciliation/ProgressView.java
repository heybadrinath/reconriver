package io.github.heybadrinath.reconriver.reconciliation;

import java.time.Instant;
import java.util.UUID;

public record ProgressView(
        UUID runId,
        String status,
        int total,
        int processed,
        double percent,
        long readCount,
        long writeCount,
        long skipCount,
        Instant startedAt,
        Instant updatedAt,
        String failureMessage,
        boolean restartable) {}
