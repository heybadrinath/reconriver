package io.github.heybadrinath.reconriver.datasetgenerator;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

record ScenarioData(
        GenerationRequest request,
        List<InternalTransaction> internalTransactions,
        List<ProcessorTransaction> processorTransactions,
        List<MalformedProcessorRow> malformedProcessorRows,
        List<BankSettlement> bankSettlements,
        List<ExpectedReconciliation> expectedReconciliations,
        Map<String, Long> injectedCounts,
        boolean duplicatedSourceFile,
        FailureRecoveryControls failureRecoveryControls) {}

record InternalTransaction(
        String internalPaymentId,
        String merchantOrderId,
        Instant occurredAt,
        BigDecimal grossAmount,
        String currency,
        String paymentStatus,
        String paymentMethod,
        String syntheticCustomerReference) {}

record ProcessorTransaction(
        String processorTransactionId,
        String merchantOrderId,
        String processorEventType,
        Instant processorEventTime,
        BigDecimal grossAmount,
        BigDecimal feeAmount,
        BigDecimal netAmount,
        String currency,
        String settlementBatchId,
        String processorStatus) {

    ProcessorTransaction {
        if (netAmount.compareTo(grossAmount.subtract(feeAmount)) != 0) {
            throw new IllegalArgumentException(
                    "processor net amount must equal gross amount minus fee amount");
        }
    }
}

record MalformedProcessorRow(
        String processorTransactionId,
        String merchantOrderId,
        String processorEventType,
        String processorEventTime,
        String grossAmount,
        String feeAmount,
        String netAmount,
        String currency,
        String settlementBatchId,
        String processorStatus,
        String category,
        String expectedRuleCode,
        String expectedExplanation) {}

record BankSettlement(
        String bankEntryId,
        String settlementBatchId,
        Instant bookedAt,
        BigDecimal creditedAmount,
        String currency,
        String bankReference,
        String description) {}

record ExpectedReconciliation(
        String scenarioId,
        String resultScope,
        String workKey,
        String settlementBatchId,
        String internalPaymentId,
        String processorTransactionId,
        String bankEntryId,
        String expectedOutcome,
        String expectedReasonCode,
        BigDecimal expectedDifference,
        String explanation) {}

record FailureRecoveryControls(
        int controlledFailureAfterRows,
        int restartChunkSize,
        int controlledFailureAfterChunks,
        String restartExpectation) {}
