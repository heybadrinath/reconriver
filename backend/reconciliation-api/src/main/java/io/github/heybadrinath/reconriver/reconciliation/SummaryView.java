package io.github.heybadrinath.reconriver.reconciliation;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

public record SummaryView(
        UUID runId,
        int totalResults,
        int matchedCount,
        int exceptionCount,
        double matchedRate,
        int openExceptions,
        BigDecimal unreconciledValue,
        int settlementBatchCount,
        Long durationMillis,
        Map<String, Integer> outcomes,
        Map<String, Integer> rules) {}
