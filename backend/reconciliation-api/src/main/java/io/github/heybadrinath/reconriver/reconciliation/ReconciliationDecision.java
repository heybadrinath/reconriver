package io.github.heybadrinath.reconriver.reconciliation;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

record ReconciliationDecision(
        Outcome outcome,
        String ruleCode,
        String explanation,
        UUID internalTransactionId,
        UUID processorTransactionId,
        UUID settlementBatchId,
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
        Instant bankBookedAt) {

    boolean isMatched() {
        return outcome == Outcome.MATCHED || outcome == Outcome.REFUND_MATCHED;
    }
}
