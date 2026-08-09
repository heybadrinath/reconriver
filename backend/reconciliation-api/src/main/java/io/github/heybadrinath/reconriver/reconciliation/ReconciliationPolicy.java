package io.github.heybadrinath.reconriver.reconciliation;

import io.github.heybadrinath.reconriver.shared.config.ReconRiverProperties;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
class ReconciliationPolicy {
    private final JdbcClient jdbc;
    private final FeePolicy feePolicy;
    private final int settlementWindowDays;

    ReconciliationPolicy(JdbcClient jdbc, FeePolicy feePolicy, ReconRiverProperties properties) {
        this.jdbc = jdbc;
        this.feePolicy = feePolicy;
        this.settlementWindowDays = properties.reconciliation().settlementWindowDays();
    }

    ReconciliationDecision evaluate(ReconciliationWorkItem work) {
        if (work.workKey().startsWith("INVALID:")) {
            return invalid(work, work.sourceErrorId());
        }
        if (work.workType().equals("SETTLEMENT")) {
            return settlement(work);
        }
        return order(work);
    }

    private ReconciliationDecision invalid(ReconciliationWorkItem work, UUID errorId) {
        if (errorId == null) {
            throw new IllegalStateException("Invalid-row work is missing its quarantine reference");
        }
        return jdbc.sql(
                        "SELECT error_code,message FROM import_row_error WHERE id=:id AND workspace_id=:workspace")
                .param("id", errorId)
                .param("workspace", work.workspaceId())
                .query(
                        (rs, row) ->
                                decision(
                                        Outcome.INVALID_SOURCE_ROW,
                                        "SOURCE_ROW_" + rs.getString("error_code"),
                                        "Source row quarantined: " + rs.getString("message"),
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null))
                .single();
    }

    private ReconciliationDecision order(ReconciliationWorkItem work) {
        List<InternalRecord> internal =
                jdbc.sql(
                                """
                        SELECT * FROM internal_transaction WHERE workspace_id=:workspace AND merchant_order_id=:orderId
                        ORDER BY occurred_at,id
                        """)
                        .param("workspace", work.workspaceId())
                        .param("orderId", work.workKey())
                        .query(this::internalRecord)
                        .list();
        List<ProcessorRecord> processor =
                jdbc.sql(
                                """
                        SELECT * FROM processor_transaction WHERE workspace_id=:workspace AND merchant_order_id=:orderId
                        ORDER BY processor_event_time,id
                        """)
                        .param("workspace", work.workspaceId())
                        .param("orderId", work.workKey())
                        .query(this::processorRecord)
                        .list();
        List<ProcessorRecord> payments = processor.stream().filter(row -> !row.isRefund()).toList();
        List<ProcessorRecord> refunds =
                processor.stream().filter(ProcessorRecord::isRefund).toList();
        InternalRecord ledger = internal.isEmpty() ? null : internal.getFirst();
        ProcessorRecord payment =
                payments.isEmpty()
                        ? (processor.isEmpty() ? null : processor.getFirst())
                        : payments.getFirst();

        if (hasDuplicateInternalIdentifier(internal)) {
            return orderDecision(
                    Outcome.DUPLICATE_INTERNAL,
                    "L1_DUPLICATE_INTERNAL",
                    "More than one internal row uses the same internal payment identifier.",
                    ledger,
                    payment,
                    null);
        }
        if (hasDuplicateProcessorIdentifier(processor)) {
            return orderDecision(
                    Outcome.DUPLICATE_PROCESSOR,
                    "L1_DUPLICATE_PROCESSOR",
                    "More than one processor row uses the same processor transaction identifier.",
                    ledger,
                    payment,
                    null);
        }
        if (internal.isEmpty()) {
            return orderDecision(
                    Outcome.MISSING_INTERNAL,
                    "L1_MISSING_INTERNAL",
                    "A processor transaction has no internal ledger payment with the same merchant order ID.",
                    null,
                    payment,
                    payment == null ? null : payment.gross());
        }
        if (payments.isEmpty()) {
            return orderDecision(
                    refunds.isEmpty() ? Outcome.MISSING_PROCESSOR : Outcome.AMBIGUOUS_MATCH,
                    refunds.isEmpty() ? "L1_MISSING_PROCESSOR" : "L1_REFUND_WITHOUT_PAYMENT",
                    refunds.isEmpty()
                            ? "The internal payment has no processor transaction with the same merchant order ID."
                            : "A refund exists without an original processor payment in this workspace.",
                    ledger,
                    payment,
                    ledger.gross());
        }
        if (internal.size() != 1 || payments.size() != 1 || refunds.size() > 1) {
            return orderDecision(
                    Outcome.AMBIGUOUS_MATCH,
                    "L1_AMBIGUOUS_CARDINALITY",
                    "The merchant order ID maps to multiple distinct candidate records and requires operator review.",
                    ledger,
                    payment,
                    null);
        }
        if (!ledger.currency().equals(payment.currency())) {
            return orderDecision(
                    Outcome.CURRENCY_MISMATCH,
                    "L1_CURRENCY_EQUALITY",
                    "Internal and processor currencies differ; amounts were not compared across currencies.",
                    ledger,
                    payment,
                    null);
        }
        if (!MoneyRules.equal(ledger.gross(), payment.gross())) {
            return orderDecision(
                    Outcome.AMOUNT_MISMATCH,
                    "L1_GROSS_AMOUNT_EQUALITY",
                    "The processor gross amount differs from the internal ledger amount.",
                    ledger,
                    payment,
                    payment.gross().subtract(ledger.gross()));
        }
        if (!feePolicy.matches(payment.gross(), payment.fee())) {
            return orderDecision(
                    Outcome.FEE_MISMATCH,
                    "L1_CONFIGURED_FEE_POLICY",
                    "The processor fee differs from the configured percentage-plus-fixed fee policy.",
                    ledger,
                    payment,
                    payment.fee().subtract(feePolicy.expectedFee(payment.gross())));
        }
        if (!refunds.isEmpty()) {
            ProcessorRecord refund = refunds.getFirst();
            BigDecimal refunded = refund.gross().abs();
            if (MoneyRules.equal(refunded, payment.gross())) {
                return orderDecision(
                        Outcome.REFUND_MATCHED,
                        "L1_FULL_REFUND_LINK",
                        "The refund is linked to its original payment and reverses the complete gross amount.",
                        ledger,
                        refund,
                        BigDecimal.ZERO.setScale(MoneyRules.SCALE));
            }
            if (refunded.compareTo(payment.gross()) < 0) {
                return orderDecision(
                        Outcome.PARTIAL_REFUND,
                        "L1_PARTIAL_REFUND_LINK",
                        "The refund is linked to its original payment and reverses part of the gross amount.",
                        ledger,
                        refund,
                        payment.gross().subtract(refunded));
            }
            return orderDecision(
                    Outcome.AMBIGUOUS_MATCH,
                    "L1_REFUND_EXCEEDS_PAYMENT",
                    "The refund exceeds the original payment and cannot be accepted automatically.",
                    ledger,
                    refund,
                    refunded.subtract(payment.gross()));
        }
        return orderDecision(
                Outcome.MATCHED,
                "L1_EXACT_ORDER_MATCH",
                "Merchant order ID, currency, gross amount and configured processor fee all match.",
                ledger,
                payment,
                BigDecimal.ZERO.setScale(MoneyRules.SCALE));
    }

    private ReconciliationDecision settlement(ReconciliationWorkItem work) {
        if (work.workKey().startsWith("BANK_ONLY:")) {
            String bankEntryId = work.workKey().substring("BANK_ONLY:".length());
            BankRecord bank =
                    jdbc.sql(
                                    "SELECT * FROM bank_settlement WHERE bank_entry_id=:entry AND workspace_id=:workspace ORDER BY id LIMIT 1")
                            .param("entry", bankEntryId)
                            .param("workspace", work.workspaceId())
                            .query(this::bankRecord)
                            .single();
            return decision(
                    Outcome.MISSING_PROCESSOR,
                    "L2_BANK_WITHOUT_PROCESSOR_BATCH",
                    "The bank entry references a settlement batch that has no processor transactions.",
                    null,
                    null,
                    null,
                    bank.id(),
                    null,
                    null,
                    bank.reference(),
                    null,
                    null,
                    null,
                    bank.credit(),
                    bank.credit(),
                    bank.currency(),
                    null,
                    null,
                    bank.bookedAt());
        }
        SettlementRecord batch =
                jdbc.sql(
                                "SELECT * FROM settlement_batch WHERE external_batch_id=:batch AND workspace_id=:workspace")
                        .param("batch", work.workKey())
                        .param("workspace", work.workspaceId())
                        .query(this::settlementRecord)
                        .single();
        List<BankRecord> banks =
                jdbc.sql(
                                """
                        SELECT * FROM bank_settlement WHERE workspace_id=:workspace AND settlement_batch_id=:batch
                        ORDER BY booked_at,id
                        """)
                        .param("workspace", work.workspaceId())
                        .param("batch", batch.externalBatchId())
                        .query(this::bankRecord)
                        .list();
        BankRecord bank = banks.isEmpty() ? null : banks.getFirst();
        if (banks.isEmpty()) {
            return settlementDecision(
                    Outcome.MISSING_BANK_SETTLEMENT,
                    "L2_MISSING_BANK_DEPOSIT",
                    "No bank deposit references the processor settlement batch.",
                    batch,
                    null,
                    batch.expectedCredit().negate());
        }
        if (banks.size() > 1 || hasDuplicateBankIdentifier(banks)) {
            return settlementDecision(
                    Outcome.DUPLICATE_BANK_ENTRY,
                    "L2_DUPLICATE_BANK_DEPOSIT",
                    "Multiple bank entries reference the same processor settlement batch.",
                    batch,
                    bank,
                    null);
        }
        if (!batch.currency().equals(bank.currency())) {
            return settlementDecision(
                    Outcome.CURRENCY_MISMATCH,
                    "L2_CURRENCY_EQUALITY",
                    "The processor settlement currency differs from the bank entry currency.",
                    batch,
                    bank,
                    null);
        }
        BigDecimal difference = bank.credit().subtract(batch.expectedCredit());
        if (!MoneyRules.equal(batch.expectedCredit(), bank.credit())) {
            return settlementDecision(
                    Outcome.AMOUNT_MISMATCH,
                    "L2_SETTLEMENT_AMOUNT_EQUALITY",
                    "The bank credit differs from the sum of processor net amounts in the settlement batch.",
                    batch,
                    bank,
                    difference);
        }
        if (bank.bookedAt()
                .isAfter(batch.lastEventAt().plus(settlementWindowDays, ChronoUnit.DAYS))) {
            return settlementDecision(
                    Outcome.LATE_SETTLEMENT,
                    "L2_SETTLEMENT_WINDOW",
                    "The bank booked the deposit outside the configured settlement window.",
                    batch,
                    bank,
                    difference);
        }
        return settlementDecision(
                Outcome.MATCHED,
                "L2_EXACT_SETTLEMENT_MATCH",
                "The bank entry matches the processor batch currency, expected net credit and settlement window.",
                batch,
                bank,
                difference);
    }

    private ReconciliationDecision orderDecision(
            Outcome outcome,
            String rule,
            String explanation,
            InternalRecord internal,
            ProcessorRecord processor,
            BigDecimal difference) {
        return decision(
                outcome,
                rule,
                explanation,
                internal == null ? null : internal.id(),
                processor == null ? null : processor.id(),
                null,
                null,
                internal == null ? null : internal.paymentReference(),
                processor == null ? null : processor.reference(),
                null,
                internal != null ? internal.gross() : processor == null ? null : processor.gross(),
                processor == null ? null : processor.fee(),
                processor == null ? null : processor.net(),
                null,
                difference == null ? null : MoneyRules.normalize(difference),
                internal != null
                        ? internal.currency()
                        : processor == null ? null : processor.currency(),
                internal == null ? null : internal.occurredAt(),
                processor == null ? null : processor.eventAt(),
                null);
    }

    private ReconciliationDecision settlementDecision(
            Outcome outcome,
            String rule,
            String explanation,
            SettlementRecord batch,
            BankRecord bank,
            BigDecimal difference) {
        return decision(
                outcome,
                rule,
                explanation,
                null,
                null,
                batch.id(),
                bank == null ? null : bank.id(),
                null,
                null,
                bank == null ? null : bank.reference(),
                null,
                null,
                batch.expectedCredit(),
                bank == null ? null : bank.credit(),
                difference == null ? null : MoneyRules.normalize(difference),
                batch.currency(),
                null,
                batch.lastEventAt(),
                bank == null ? null : bank.bookedAt());
    }

    private ReconciliationDecision decision(
            Outcome outcome,
            String rule,
            String explanation,
            UUID internalId,
            UUID processorId,
            UUID settlementId,
            UUID bankId,
            String internalReference,
            String processorReference,
            String bankReference,
            BigDecimal gross,
            BigDecimal fee,
            BigDecimal net,
            BigDecimal bankCredit,
            BigDecimal difference,
            String currency,
            Instant internalAt,
            Instant processorAt,
            Instant bankAt) {
        return new ReconciliationDecision(
                outcome,
                rule,
                explanation,
                internalId,
                processorId,
                settlementId,
                bankId,
                internalReference,
                processorReference,
                bankReference,
                MoneyRules.normalize(gross),
                MoneyRules.normalize(fee),
                MoneyRules.normalize(net),
                MoneyRules.normalize(bankCredit),
                MoneyRules.normalize(difference),
                currency,
                internalAt,
                processorAt,
                bankAt);
    }

    private boolean hasDuplicateInternalIdentifier(List<InternalRecord> records) {
        Set<String> identifiers = new HashSet<>();
        return records.stream().anyMatch(record -> !identifiers.add(record.paymentReference()));
    }

    private boolean hasDuplicateProcessorIdentifier(List<ProcessorRecord> records) {
        Set<String> identifiers = new HashSet<>();
        return records.stream().anyMatch(record -> !identifiers.add(record.reference()));
    }

    private boolean hasDuplicateBankIdentifier(List<BankRecord> records) {
        Set<String> identifiers = new HashSet<>();
        return records.stream().anyMatch(record -> !identifiers.add(record.reference()));
    }

    private InternalRecord internalRecord(ResultSet rs, int row) throws SQLException {
        return new InternalRecord(
                rs.getObject("id", UUID.class),
                rs.getString("internal_payment_id"),
                rs.getBigDecimal("gross_amount"),
                rs.getString("currency"),
                rs.getTimestamp("occurred_at").toInstant());
    }

    private ProcessorRecord processorRecord(ResultSet rs, int row) throws SQLException {
        return new ProcessorRecord(
                rs.getObject("id", UUID.class),
                rs.getString("processor_transaction_id"),
                rs.getString("processor_event_type"),
                rs.getBigDecimal("gross_amount"),
                rs.getBigDecimal("fee_amount"),
                rs.getBigDecimal("net_amount"),
                rs.getString("currency"),
                rs.getTimestamp("processor_event_time").toInstant());
    }

    private SettlementRecord settlementRecord(ResultSet rs, int row) throws SQLException {
        return new SettlementRecord(
                rs.getObject("id", UUID.class),
                rs.getString("external_batch_id"),
                rs.getString("currency"),
                rs.getBigDecimal("expected_credit"),
                rs.getTimestamp("last_event_at").toInstant());
    }

    private BankRecord bankRecord(ResultSet rs, int row) throws SQLException {
        return new BankRecord(
                rs.getObject("id", UUID.class),
                rs.getString("bank_entry_id"),
                rs.getBigDecimal("credited_amount"),
                rs.getString("currency"),
                rs.getTimestamp("booked_at").toInstant());
    }

    private record InternalRecord(
            UUID id,
            String paymentReference,
            BigDecimal gross,
            String currency,
            Instant occurredAt) {}

    private record ProcessorRecord(
            UUID id,
            String reference,
            String eventType,
            BigDecimal gross,
            BigDecimal fee,
            BigDecimal net,
            String currency,
            Instant eventAt) {
        boolean isRefund() {
            return eventType.equalsIgnoreCase("REFUND");
        }
    }

    private record SettlementRecord(
            UUID id,
            String externalBatchId,
            String currency,
            BigDecimal expectedCredit,
            Instant lastEventAt) {}

    private record BankRecord(
            UUID id, String reference, BigDecimal credit, String currency, Instant bookedAt) {}
}
