package io.github.heybadrinath.reconriver.datasetgenerator;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class GroundTruthBuilder {
    private static final int SETTLEMENT_WINDOW_DAYS = 3;

    List<ExpectedReconciliation> build(
            GenerationRequest request,
            List<InternalTransaction> internalTransactions,
            List<ProcessorTransaction> processorTransactions,
            List<MalformedProcessorRow> malformedProcessorRows,
            List<BankSettlement> bankSettlements) {
        List<ExpectedReconciliation> expected = new ArrayList<>();
        expected.addAll(orderResults(request, internalTransactions, processorTransactions));
        expected.addAll(invalidResults(request, malformedProcessorRows));
        expected.addAll(settlementResults(request, processorTransactions, bankSettlements));
        return List.copyOf(expected);
    }

    private List<ExpectedReconciliation> orderResults(
            GenerationRequest request,
            List<InternalTransaction> internalTransactions,
            List<ProcessorTransaction> processorTransactions) {
        Map<String, List<InternalTransaction>> internalByOrder =
                internalTransactions.stream()
                        .collect(
                                Collectors.groupingBy(
                                        InternalTransaction::merchantOrderId,
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        Map<String, List<ProcessorTransaction>> processorByOrder =
                processorTransactions.stream()
                        .collect(
                                Collectors.groupingBy(
                                        ProcessorTransaction::merchantOrderId,
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        Set<String> workKeys = new TreeSet<>(internalByOrder.keySet());
        workKeys.addAll(processorByOrder.keySet());

        List<ExpectedReconciliation> results = new ArrayList<>(workKeys.size());
        for (String workKey : workKeys) {
            List<InternalTransaction> internals =
                    new ArrayList<>(internalByOrder.getOrDefault(workKey, List.of()));
            internals.sort(
                    Comparator.comparing(InternalTransaction::occurredAt)
                            .thenComparing(InternalTransaction::internalPaymentId));
            List<ProcessorTransaction> processors =
                    new ArrayList<>(processorByOrder.getOrDefault(workKey, List.of()));
            processors.sort(
                    Comparator.comparing(ProcessorTransaction::processorEventTime)
                            .thenComparing(ProcessorTransaction::processorTransactionId));
            results.add(evaluateOrder(request.scenario().slug(), workKey, internals, processors));
        }
        return results;
    }

    private ExpectedReconciliation evaluateOrder(
            String scenarioId,
            String workKey,
            List<InternalTransaction> internals,
            List<ProcessorTransaction> processors) {
        List<ProcessorTransaction> payments =
                processors.stream().filter(transaction -> !isRefund(transaction)).toList();
        List<ProcessorTransaction> refunds = processors.stream().filter(this::isRefund).toList();
        InternalTransaction ledger = internals.isEmpty() ? null : internals.getFirst();
        ProcessorTransaction payment =
                payments.isEmpty()
                        ? processors.isEmpty() ? null : processors.getFirst()
                        : payments.getFirst();

        if (hasDuplicateInternalIdentifier(internals)) {
            return orderDecision(
                    scenarioId,
                    workKey,
                    "DUPLICATE_INTERNAL",
                    "L1_DUPLICATE_INTERNAL",
                    "More than one internal row uses the same internal payment identifier.",
                    ledger,
                    payment,
                    null);
        }
        if (hasDuplicateProcessorIdentifier(processors)) {
            return orderDecision(
                    scenarioId,
                    workKey,
                    "DUPLICATE_PROCESSOR",
                    "L1_DUPLICATE_PROCESSOR",
                    "More than one processor row uses the same processor transaction identifier.",
                    ledger,
                    payment,
                    null);
        }
        if (internals.isEmpty()) {
            return orderDecision(
                    scenarioId,
                    workKey,
                    "MISSING_INTERNAL",
                    "L1_MISSING_INTERNAL",
                    "A processor transaction has no internal ledger payment with the same merchant order ID.",
                    null,
                    payment,
                    payment == null ? null : payment.grossAmount());
        }
        if (payments.isEmpty()) {
            boolean refundWithoutPayment = !refunds.isEmpty();
            return orderDecision(
                    scenarioId,
                    workKey,
                    refundWithoutPayment ? "AMBIGUOUS_MATCH" : "MISSING_PROCESSOR",
                    refundWithoutPayment ? "L1_REFUND_WITHOUT_PAYMENT" : "L1_MISSING_PROCESSOR",
                    refundWithoutPayment
                            ? "A refund exists without an original processor payment in this workspace."
                            : "The internal payment has no processor transaction with the same merchant order ID.",
                    ledger,
                    payment,
                    refundWithoutPayment ? ledger.grossAmount() : ledger.grossAmount());
        }
        if (internals.size() != 1 || payments.size() != 1 || refunds.size() > 1) {
            return orderDecision(
                    scenarioId,
                    workKey,
                    "AMBIGUOUS_MATCH",
                    "L1_AMBIGUOUS_CARDINALITY",
                    "The merchant order ID maps to multiple distinct candidate records and requires operator review.",
                    ledger,
                    payment,
                    null);
        }
        if (!ledger.currency().equals(payment.currency())) {
            return orderDecision(
                    scenarioId,
                    workKey,
                    "CURRENCY_MISMATCH",
                    "L1_CURRENCY_EQUALITY",
                    "Internal and processor currencies differ; amounts were not compared across currencies.",
                    ledger,
                    payment,
                    null);
        }
        if (ledger.grossAmount().compareTo(payment.grossAmount()) != 0) {
            return orderDecision(
                    scenarioId,
                    workKey,
                    "AMOUNT_MISMATCH",
                    "L1_GROSS_AMOUNT_EQUALITY",
                    "The processor gross amount differs from the internal ledger amount.",
                    ledger,
                    payment,
                    payment.grossAmount().subtract(ledger.grossAmount()));
        }
        if (MoneyPolicy.feeFor(payment.grossAmount()).compareTo(payment.feeAmount()) != 0) {
            return orderDecision(
                    scenarioId,
                    workKey,
                    "FEE_MISMATCH",
                    "L1_CONFIGURED_FEE_POLICY",
                    "The processor fee differs from the configured percentage-plus-fixed fee policy.",
                    ledger,
                    payment,
                    payment.feeAmount().subtract(MoneyPolicy.feeFor(payment.grossAmount())));
        }
        if (!refunds.isEmpty()) {
            ProcessorTransaction refund = refunds.getFirst();
            BigDecimal refunded = refund.grossAmount().abs();
            if (refunded.compareTo(payment.grossAmount()) == 0) {
                return orderDecision(
                        scenarioId,
                        workKey,
                        "REFUND_MATCHED",
                        "L1_FULL_REFUND_LINK",
                        "The refund is linked to its original payment and reverses the complete gross amount.",
                        ledger,
                        refund,
                        zero());
            }
            if (refunded.compareTo(payment.grossAmount()) < 0) {
                return orderDecision(
                        scenarioId,
                        workKey,
                        "PARTIAL_REFUND",
                        "L1_PARTIAL_REFUND_LINK",
                        "The refund is linked to its original payment and reverses part of the gross amount.",
                        ledger,
                        refund,
                        payment.grossAmount().subtract(refunded));
            }
            return orderDecision(
                    scenarioId,
                    workKey,
                    "AMBIGUOUS_MATCH",
                    "L1_REFUND_EXCEEDS_PAYMENT",
                    "The refund exceeds the original payment and cannot be accepted automatically.",
                    ledger,
                    refund,
                    refunded.subtract(payment.grossAmount()));
        }
        return orderDecision(
                scenarioId,
                workKey,
                "MATCHED",
                "L1_EXACT_ORDER_MATCH",
                "Merchant order ID, currency, gross amount and configured processor fee all match.",
                ledger,
                payment,
                zero());
    }

    private List<ExpectedReconciliation> invalidResults(
            GenerationRequest request, List<MalformedProcessorRow> malformedRows) {
        return malformedRows.stream()
                .sorted(Comparator.comparing(MalformedProcessorRow::processorTransactionId))
                .map(
                        row ->
                                new ExpectedReconciliation(
                                        request.scenario().slug(),
                                        "ORDER",
                                        "INVALID:" + row.processorTransactionId(),
                                        "",
                                        "",
                                        "",
                                        "",
                                        "INVALID_SOURCE_ROW",
                                        row.expectedRuleCode(),
                                        null,
                                        row.expectedExplanation()))
                .toList();
    }

    private List<ExpectedReconciliation> settlementResults(
            GenerationRequest request,
            List<ProcessorTransaction> processorTransactions,
            List<BankSettlement> bankSettlements) {
        Map<BatchCurrency, BatchAggregate> batches = new LinkedHashMap<>();
        for (ProcessorTransaction transaction : processorTransactions) {
            BatchCurrency key =
                    new BatchCurrency(transaction.settlementBatchId(), transaction.currency());
            BatchAggregate current = batches.get(key);
            batches.put(
                    key,
                    current == null
                            ? new BatchAggregate(
                                    transaction.netAmount(), transaction.processorEventTime(), 1)
                            : new BatchAggregate(
                                    current.expectedCredit().add(transaction.netAmount()),
                                    current.lastEventAt().isAfter(transaction.processorEventTime())
                                            ? current.lastEventAt()
                                            : transaction.processorEventTime(),
                                    current.transactionCount() + 1));
        }
        Map<String, List<BankSettlement>> banksByBatch =
                bankSettlements.stream()
                        .collect(
                                Collectors.groupingBy(
                                        BankSettlement::settlementBatchId,
                                        LinkedHashMap::new,
                                        Collectors.toList()));
        banksByBatch
                .values()
                .forEach(
                        banks ->
                                banks.sort(
                                        Comparator.comparing(BankSettlement::bookedAt)
                                                .thenComparing(BankSettlement::bankEntryId)));

        List<Map.Entry<BatchCurrency, BatchAggregate>> orderedBatches =
                new ArrayList<>(batches.entrySet());
        orderedBatches.sort(
                Map.Entry.comparingByKey(
                        Comparator.comparing(BatchCurrency::batchId)
                                .thenComparing(BatchCurrency::currency)));
        List<ExpectedReconciliation> results = new ArrayList<>();
        for (Map.Entry<BatchCurrency, BatchAggregate> entry : orderedBatches) {
            BatchCurrency key = entry.getKey();
            BatchAggregate batch = entry.getValue();
            List<BankSettlement> banks = banksByBatch.getOrDefault(key.batchId(), List.of());
            BankSettlement bank = banks.isEmpty() ? null : banks.getFirst();
            results.add(evaluateSettlement(request.scenario().slug(), key, batch, banks, bank));
        }

        Set<String> processorBatchIds =
                processorTransactions.stream()
                        .map(ProcessorTransaction::settlementBatchId)
                        .collect(Collectors.toSet());
        bankSettlements.stream()
                .filter(bank -> !processorBatchIds.contains(bank.settlementBatchId()))
                .sorted(
                        Comparator.comparing(BankSettlement::settlementBatchId)
                                .thenComparing(BankSettlement::bankEntryId))
                .map(
                        bank ->
                                new ExpectedReconciliation(
                                        request.scenario().slug(),
                                        "SETTLEMENT",
                                        "BANK_ONLY:" + bank.bankEntryId(),
                                        bank.settlementBatchId(),
                                        "",
                                        "",
                                        bank.bankEntryId(),
                                        "MISSING_PROCESSOR",
                                        "L2_BANK_WITHOUT_PROCESSOR_BATCH",
                                        bank.creditedAmount(),
                                        "The bank entry references a settlement batch that has no processor transactions."))
                .forEach(results::add);
        return results;
    }

    private ExpectedReconciliation evaluateSettlement(
            String scenarioId,
            BatchCurrency key,
            BatchAggregate batch,
            List<BankSettlement> banks,
            BankSettlement bank) {
        if (banks.isEmpty()) {
            return settlementDecision(
                    scenarioId,
                    key.batchId(),
                    null,
                    "MISSING_BANK_SETTLEMENT",
                    "L2_MISSING_BANK_DEPOSIT",
                    "No bank deposit references the processor settlement batch.",
                    batch.expectedCredit().negate());
        }
        if (banks.size() > 1 || hasDuplicateBankIdentifier(banks)) {
            return settlementDecision(
                    scenarioId,
                    key.batchId(),
                    bank,
                    "DUPLICATE_BANK_ENTRY",
                    "L2_DUPLICATE_BANK_DEPOSIT",
                    "Multiple bank entries reference the same processor settlement batch.",
                    null);
        }
        if (!key.currency().equals(bank.currency())) {
            return settlementDecision(
                    scenarioId,
                    key.batchId(),
                    bank,
                    "CURRENCY_MISMATCH",
                    "L2_CURRENCY_EQUALITY",
                    "The processor settlement currency differs from the bank entry currency.",
                    null);
        }
        BigDecimal difference = bank.creditedAmount().subtract(batch.expectedCredit());
        if (difference.compareTo(zero()) != 0) {
            return settlementDecision(
                    scenarioId,
                    key.batchId(),
                    bank,
                    "AMOUNT_MISMATCH",
                    "L2_SETTLEMENT_AMOUNT_EQUALITY",
                    "The bank credit differs from the sum of processor net amounts in the settlement batch.",
                    difference);
        }
        if (bank.bookedAt()
                .isAfter(batch.lastEventAt().plus(SETTLEMENT_WINDOW_DAYS, ChronoUnit.DAYS))) {
            return settlementDecision(
                    scenarioId,
                    key.batchId(),
                    bank,
                    "LATE_SETTLEMENT",
                    "L2_SETTLEMENT_WINDOW",
                    "The bank booked the deposit outside the configured settlement window.",
                    difference);
        }
        return settlementDecision(
                scenarioId,
                key.batchId(),
                bank,
                "MATCHED",
                "L2_EXACT_SETTLEMENT_MATCH",
                "The bank entry matches the processor batch currency, expected net credit and settlement window.",
                difference);
    }

    private ExpectedReconciliation orderDecision(
            String scenarioId,
            String workKey,
            String outcome,
            String rule,
            String explanation,
            InternalTransaction internal,
            ProcessorTransaction processor,
            BigDecimal difference) {
        return new ExpectedReconciliation(
                scenarioId,
                "ORDER",
                workKey,
                "",
                internal == null ? "" : internal.internalPaymentId(),
                processor == null ? "" : processor.processorTransactionId(),
                "",
                outcome,
                rule,
                normalize(difference),
                explanation);
    }

    private ExpectedReconciliation settlementDecision(
            String scenarioId,
            String batchId,
            BankSettlement bank,
            String outcome,
            String rule,
            String explanation,
            BigDecimal difference) {
        return new ExpectedReconciliation(
                scenarioId,
                "SETTLEMENT",
                batchId,
                batchId,
                "",
                "",
                bank == null ? "" : bank.bankEntryId(),
                outcome,
                rule,
                normalize(difference),
                explanation);
    }

    private boolean isRefund(ProcessorTransaction transaction) {
        return "REFUND".equalsIgnoreCase(transaction.processorEventType());
    }

    private boolean hasDuplicateInternalIdentifier(List<InternalTransaction> records) {
        Set<String> identifiers = new HashSet<>();
        return records.stream().anyMatch(record -> !identifiers.add(record.internalPaymentId()));
    }

    private boolean hasDuplicateProcessorIdentifier(List<ProcessorTransaction> records) {
        Set<String> identifiers = new HashSet<>();
        return records.stream()
                .anyMatch(record -> !identifiers.add(record.processorTransactionId()));
    }

    private boolean hasDuplicateBankIdentifier(List<BankSettlement> records) {
        Set<String> identifiers = new HashSet<>();
        return records.stream().anyMatch(record -> !identifiers.add(record.bankEntryId()));
    }

    private BigDecimal normalize(BigDecimal value) {
        return value == null ? null : MoneyPolicy.scaled(value);
    }

    private BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MoneyPolicy.SCALE);
    }

    private record BatchCurrency(String batchId, String currency) {}

    private record BatchAggregate(
            BigDecimal expectedCredit, java.time.Instant lastEventAt, int transactionCount) {}
}
