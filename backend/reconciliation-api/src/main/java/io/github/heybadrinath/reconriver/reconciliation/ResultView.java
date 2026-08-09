package io.github.heybadrinath.reconriver.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record ResultView(
        UUID id,
        UUID runId,
        ResultScope resultScope,
        String workKey,
        Outcome outcome,
        String ruleCode,
        String explanation,
        String resolutionStatus,
        UUID internalTransactionId,
        UUID processorTransactionId,
        UUID settlementBatchId,
        String settlementBatchReference,
        UUID bankSettlementId,
        String internalPaymentReference,
        String processorTransactionReference,
        String bankEntryReference,
        BigDecimal grossAmount,
        BigDecimal processorFee,
        BigDecimal netAmount,
        BigDecimal bankCredit,
        BigDecimal difference,
        String currency,
        Instant internalOccurredAt,
        Instant processorEventAt,
        Instant bankBookedAt,
        Instant createdAt,
        Instant updatedAt) {}
