package io.github.heybadrinath.reconriver.reporting;

import io.github.heybadrinath.reconriver.audit.AuditEventView;
import io.github.heybadrinath.reconriver.reconciliation.ResultView;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record TransactionTraceView(
        UUID transactionId,
        InternalNode internal,
        ProcessorNode processor,
        SettlementNode settlementBatch,
        BankNode bank,
        ProcessorNode refund,
        List<ResultView> decisions,
        List<AuditEventView> auditEvents) {

    public record InternalNode(
            UUID id,
            String internalPaymentId,
            String merchantOrderId,
            Instant occurredAt,
            BigDecimal grossAmount,
            String currency,
            String paymentStatus,
            String paymentMethod,
            String syntheticCustomerReference) {}

    public record ProcessorNode(
            UUID id,
            String processorTransactionId,
            String merchantOrderId,
            String processorEventType,
            Instant processorEventTime,
            BigDecimal grossAmount,
            BigDecimal feeAmount,
            BigDecimal netAmount,
            String currency,
            String settlementBatchId,
            String processorStatus) {}

    public record SettlementNode(
            UUID id,
            String settlementBatchId,
            String currency,
            BigDecimal expectedCredit,
            Instant firstEventAt,
            Instant lastEventAt,
            int transactionCount) {}

    public record BankNode(
            UUID id,
            String bankEntryId,
            String settlementBatchId,
            Instant bookedAt,
            BigDecimal creditedAmount,
            String currency,
            String bankReference,
            String description) {}
}
