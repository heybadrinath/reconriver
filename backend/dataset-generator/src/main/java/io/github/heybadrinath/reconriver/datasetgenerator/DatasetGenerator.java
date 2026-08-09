package io.github.heybadrinath.reconriver.datasetgenerator;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class DatasetGenerator {
    private static final int SETTLEMENT_WINDOW_DAYS = 3;
    private static final int BATCH_SIZE = 25;

    public GeneratedScenario generate(GenerationRequest request) {
        ScenarioData scenarioData = buildScenario(request);
        return new ScenarioPackWriter().write(scenarioData);
    }

    ScenarioData buildScenario(GenerationRequest request) {
        List<CanonicalPayment> canonicalPayments = canonicalPayments(request);
        InjectionPlan injectionPlan = InjectionPlan.create(request);

        List<InternalTransaction> internalTransactions = new ArrayList<>();
        List<ProcessorTransaction> processorTransactions = new ArrayList<>();
        List<MalformedProcessorRow> malformedProcessorRows = new ArrayList<>();

        for (CanonicalPayment payment : canonicalPayments) {
            InjectionKind injection = injectionPlan.injectionFor(payment.sequence());
            InternalTransaction internal = toInternal(payment, injection);
            internalTransactions.add(internal);
            if (injection == InjectionKind.DUPLICATE_INTERNAL) {
                internalTransactions.add(internal);
            }
            if (injection == InjectionKind.MISSING_PROCESSOR) {
                continue;
            }

            ProcessorTransaction processor = toProcessor(payment, injection);
            processorTransactions.add(processor);
            if (injection == InjectionKind.DUPLICATE_PROCESSOR) {
                processorTransactions.add(processor);
            }
            if (injection == InjectionKind.AMBIGUOUS_MATCH) {
                processorTransactions.add(toAmbiguousProcessor(processor, payment.sequence()));
            }
            if (injection == InjectionKind.FULL_REFUND
                    || injection == InjectionKind.PARTIAL_REFUND) {
                processorTransactions.add(toRefund(payment, injection));
            }
        }

        List<ProcessorOnlyRecord> processorOnlyRecords =
                addProcessorOnlyRecords(request, processorTransactions);
        malformedProcessorRows.addAll(malformedRows(request));

        BankBuildResult bankBuildResult = buildBankSettlements(processorTransactions);
        int bankOnlyCount = addBankOnlySettlements(request, bankBuildResult.bankSettlements());
        List<ExpectedReconciliation> expected =
                new GroundTruthBuilder()
                        .build(
                                request,
                                internalTransactions,
                                processorTransactions,
                                malformedProcessorRows,
                                bankBuildResult.bankSettlements());

        Map<String, Long> injectedCounts = new TreeMap<>(injectionPlan.actualCounts());
        mergeCount(injectedCounts, "processor_only_records", processorOnlyRecords.size());
        for (MalformedProcessorRow row : malformedProcessorRows) {
            mergeCount(injectedCounts, row.category(), 1);
        }
        mergeCount(injectedCounts, "bank_only_entries", bankOnlyCount);
        if (request.scenario() == ScenarioType.MIXED_EXCEPTIONS) {
            mergeCount(injectedCounts, "duplicated_source_files", 1);
        }

        FailureRecoveryControls controls = null;
        if (request.scenario() == ScenarioType.FAILURE_RECOVERY) {
            int chunkSize = Math.min(500, Math.max(1, request.recordCount() / 20));
            int failureAfterChunks = Math.min(5, Math.ceilDiv(request.recordCount(), chunkSize));
            int failureAfterRows = Math.min(request.recordCount(), failureAfterChunks * chunkSize);
            controls =
                    new FailureRecoveryControls(
                            failureAfterRows,
                            chunkSize,
                            failureAfterChunks,
                            "Restart from the last committed chunk without creating duplicate reconciliation results.");
        }

        return new ScenarioData(
                request,
                List.copyOf(internalTransactions),
                List.copyOf(processorTransactions),
                List.copyOf(malformedProcessorRows),
                List.copyOf(bankBuildResult.bankSettlements()),
                List.copyOf(expected),
                Collections.unmodifiableMap(injectedCounts),
                request.scenario() == ScenarioType.MIXED_EXCEPTIONS,
                controls);
    }

    private List<CanonicalPayment> canonicalPayments(GenerationRequest request) {
        SplittableRandom random = new SplittableRandom(request.seed());
        int daySpan = daySpan(request.scenario());
        List<CanonicalPayment> payments = new ArrayList<>(request.recordCount());
        for (int index = 0; index < request.recordCount(); index++) {
            int sequence = index + 1;
            long minorUnits = random.nextLong(500, 250_001);
            BigDecimal grossAmount = MoneyPolicy.money(minorUnits);
            LocalDate eventDate = request.startDate().plusDays(index % daySpan);
            long secondOfDay =
                    Math.floorMod((long) sequence * 7_919L + random.nextLong(0, 3_600), 86_400L);
            Instant occurredAt =
                    eventDate.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(secondOfDay);
            String currency = currencyFor(request.scenario(), sequence);
            payments.add(
                    new CanonicalPayment(
                            sequence,
                            "SYNTH-INT-%06d".formatted(sequence),
                            "SYNTH-ORDER-%06d".formatted(sequence),
                            occurredAt,
                            grossAmount,
                            currency,
                            "SYNTH-CUSTOMER-%06d".formatted(sequence),
                            daySpan));
        }
        return payments;
    }

    private int daySpan(ScenarioType scenario) {
        return switch (scenario) {
            case CLEAN_SETTLEMENT -> 7;
            case MIXED_EXCEPTIONS -> 14;
            case MONTH_END_CLOSE, FAILURE_RECOVERY -> 31;
            case LOCAL_PERFORMANCE -> 90;
        };
    }

    private String currencyFor(ScenarioType scenario, int sequence) {
        if (scenario == ScenarioType.CLEAN_SETTLEMENT) {
            return "USD";
        }
        return switch (sequence % 10) {
            case 0 -> "EUR";
            case 1 -> "GBP";
            default -> "USD";
        };
    }

    private InternalTransaction toInternal(CanonicalPayment payment, InjectionKind injection) {
        String paymentStatus =
                switch (injection) {
                    case FULL_REFUND -> "REFUNDED";
                    case PARTIAL_REFUND -> "PARTIALLY_REFUNDED";
                    default -> "CAPTURED";
                };
        String paymentMethod =
                switch (payment.sequence() % 3) {
                    case 0 -> "CARD_SYNTHETIC";
                    case 1 -> "WALLET_SYNTHETIC";
                    default -> "TRANSFER_SYNTHETIC";
                };
        return new InternalTransaction(
                payment.internalPaymentId(),
                payment.merchantOrderId(),
                payment.occurredAt(),
                payment.grossAmount(),
                payment.currency(),
                paymentStatus,
                paymentMethod,
                payment.syntheticCustomerReference());
    }

    private ProcessorTransaction toProcessor(CanonicalPayment payment, InjectionKind injection) {
        BigDecimal grossAmount =
                injection == InjectionKind.AMOUNT_MISMATCH
                        ? payment.grossAmount().add(new BigDecimal("1.11"))
                        : payment.grossAmount();
        String currency =
                injection == InjectionKind.CURRENCY_MISMATCH
                        ? differentCurrency(payment.currency())
                        : payment.currency();
        BigDecimal fee = MoneyPolicy.feeFor(grossAmount);
        if (injection == InjectionKind.FEE_MISMATCH) {
            fee = fee.add(new BigDecimal("0.37"));
        }
        fee = MoneyPolicy.scaled(fee);
        String batchId = batchId(payment, currency, injection, "CAPTURE");
        return new ProcessorTransaction(
                "SYNTH-PROC-%06d".formatted(payment.sequence()),
                payment.merchantOrderId(),
                "CAPTURE",
                payment.occurredAt().plus(10, ChronoUnit.MINUTES),
                MoneyPolicy.scaled(grossAmount),
                fee,
                MoneyPolicy.scaled(grossAmount.subtract(fee)),
                currency,
                batchId,
                "SETTLED");
    }

    private ProcessorTransaction toRefund(CanonicalPayment payment, InjectionKind injection) {
        BigDecimal refundMagnitude =
                injection == InjectionKind.FULL_REFUND
                        ? payment.grossAmount()
                        : payment.grossAmount()
                                .divide(
                                        new BigDecimal("2"),
                                        MoneyPolicy.SCALE,
                                        RoundingMode.HALF_UP);
        BigDecimal refundGross = refundMagnitude.negate();
        BigDecimal fee = BigDecimal.ZERO.setScale(MoneyPolicy.SCALE);
        return new ProcessorTransaction(
                "SYNTH-PROC-%06d-REFUND".formatted(payment.sequence()),
                payment.merchantOrderId(),
                "REFUND",
                payment.occurredAt().plus(1, ChronoUnit.DAYS),
                refundGross,
                fee,
                refundGross,
                payment.currency(),
                batchId(payment, payment.currency(), injection, "REFUND"),
                "SETTLED");
    }

    private ProcessorTransaction toAmbiguousProcessor(ProcessorTransaction primary, int sequence) {
        return new ProcessorTransaction(
                "SYNTH-PROC-%06d-ALTERNATE".formatted(sequence),
                primary.merchantOrderId(),
                primary.processorEventType(),
                primary.processorEventTime().plus(1, ChronoUnit.MINUTES),
                primary.grossAmount(),
                primary.feeAmount(),
                primary.netAmount(),
                primary.currency(),
                primary.settlementBatchId(),
                primary.processorStatus());
    }

    private String batchId(
            CanonicalPayment payment, String currency, InjectionKind injection, String eventType) {
        if ("REFUND".equals(eventType)) {
            return "SYNTH-BATCH-REFUND-%06d".formatted(payment.sequence());
        }
        return switch (injection) {
            case LATE_SETTLEMENT -> "SYNTH-BATCH-LATE-%06d".formatted(payment.sequence());
            case MISSING_BANK_SETTLEMENT ->
                    "SYNTH-BATCH-MISSING-%06d".formatted(payment.sequence());
            case DUPLICATE_BANK_ENTRY -> "SYNTH-BATCH-DUPLICATE-%06d".formatted(payment.sequence());
            case BANK_AMOUNT_MISMATCH ->
                    "SYNTH-BATCH-AMOUNT-BREAK-%06d".formatted(payment.sequence());
            case BANK_CURRENCY_MISMATCH ->
                    "SYNTH-BATCH-BANK-CURRENCY-%06d".formatted(payment.sequence());
            case CURRENCY_MISMATCH -> "SYNTH-BATCH-CURRENCY-%06d".formatted(payment.sequence());
            default -> {
                LocalDate settlementDate =
                        payment.occurredAt().atZone(ZoneOffset.UTC).toLocalDate().plusDays(2);
                int group = (payment.sequence() - 1) / (payment.daySpan() * BATCH_SIZE);
                yield "SYNTH-BATCH-%s-%s-%04d".formatted(currency, settlementDate, group);
            }
        };
    }

    private String differentCurrency(String currency) {
        return "USD".equals(currency) ? "EUR" : "USD";
    }

    private List<ProcessorOnlyRecord> addProcessorOnlyRecords(
            GenerationRequest request, List<ProcessorTransaction> processorTransactions) {
        int count =
                switch (request.scenario()) {
                    case MIXED_EXCEPTIONS -> rateCount(request.recordCount(), 2, 100);
                    case MONTH_END_CLOSE -> rateCount(request.recordCount(), 2, 1_000);
                    default -> 0;
                };
        List<ProcessorOnlyRecord> records = new ArrayList<>(count);
        SplittableRandom random = new SplittableRandom(request.seed() ^ 0x5EED5EEDL);
        for (int index = 0; index < count; index++) {
            int sequence = index + 1;
            BigDecimal gross = MoneyPolicy.money(random.nextLong(500, 100_001));
            BigDecimal fee = MoneyPolicy.feeFor(gross);
            Instant eventTime =
                    request.startDate()
                            .atStartOfDay(ZoneOffset.UTC)
                            .toInstant()
                            .plus(index + 1L, ChronoUnit.HOURS);
            ProcessorTransaction processor =
                    new ProcessorTransaction(
                            "SYNTH-PROC-ONLY-%06d".formatted(sequence),
                            "SYNTH-ORDER-PROC-ONLY-%06d".formatted(sequence),
                            "CAPTURE",
                            eventTime,
                            gross,
                            fee,
                            MoneyPolicy.scaled(gross.subtract(fee)),
                            "USD",
                            "SYNTH-BATCH-PROC-ONLY-%06d".formatted(sequence),
                            "SETTLED");
            processorTransactions.add(processor);
            records.add(new ProcessorOnlyRecord(processor));
        }
        return records;
    }

    private List<MalformedProcessorRow> malformedRows(GenerationRequest request) {
        int malformedCount;
        int invalidCount;
        int quarantinedCount;
        switch (request.scenario()) {
            case MIXED_EXCEPTIONS -> {
                malformedCount = rateCount(request.recordCount(), 5, 1_000);
                invalidCount = 0;
                quarantinedCount = 0;
            }
            case FAILURE_RECOVERY -> {
                malformedCount = 0;
                invalidCount = rateCount(request.recordCount(), 5, 1_000);
                quarantinedCount = rateCount(request.recordCount(), 5, 1_000);
            }
            default -> {
                malformedCount = 0;
                invalidCount = 0;
                quarantinedCount = 0;
            }
        }
        List<MalformedProcessorRow> rows =
                new ArrayList<>(malformedCount + invalidCount + quarantinedCount);
        String validTimestamp =
                request.startDate().atStartOfDay(ZoneOffset.UTC).toInstant().toString();
        for (int index = 1; index <= malformedCount; index++) {
            rows.add(
                    malformedRow(
                            index,
                            "malformed_rows",
                            validTimestamp,
                            "NOT_A_NUMBER",
                            "0.30",
                            "0.00",
                            "USD",
                            "SOURCE_ROW_INVALID_AMOUNT",
                            "Source row quarantined: gross_amount must be a decimal with at most four fractional digits"));
        }
        for (int index = 1; index <= invalidCount; index++) {
            rows.add(
                    malformedRow(
                            index,
                            "invalid_rows",
                            "2026-99-99T99:99:99Z",
                            "10.00",
                            "0.30",
                            "9.70",
                            "USD",
                            "SOURCE_ROW_INVALID_TIMESTAMP",
                            "Source row quarantined: processor_event_time must be an ISO-8601 timestamp"));
        }
        for (int index = 1; index <= quarantinedCount; index++) {
            rows.add(
                    malformedRow(
                            index,
                            "quarantined_rows",
                            validTimestamp,
                            "",
                            "0.30",
                            "",
                            "USD",
                            "SOURCE_ROW_INVALID_AMOUNT",
                            "Source row quarantined: gross_amount is required"));
        }
        return rows;
    }

    private MalformedProcessorRow malformedRow(
            int sequence,
            String category,
            String eventTime,
            String grossAmount,
            String feeAmount,
            String netAmount,
            String currency,
            String expectedRuleCode,
            String expectedExplanation) {
        return new MalformedProcessorRow(
                "SYNTH-PROC-%s-%06d".formatted(category.toUpperCase().replace('_', '-'), sequence),
                "SYNTH-ORDER-%s-%06d".formatted(category.toUpperCase().replace('_', '-'), sequence),
                "CAPTURE",
                eventTime,
                grossAmount,
                feeAmount,
                netAmount,
                currency,
                "SYNTH-BATCH-%s-%06d".formatted(category.toUpperCase().replace('_', '-'), sequence),
                "SETTLED",
                category,
                expectedRuleCode,
                expectedExplanation);
    }

    private BankBuildResult buildBankSettlements(List<ProcessorTransaction> processorTransactions) {
        Map<String, ProcessorTransaction> uniqueProcessors =
                processorTransactions.stream()
                        .collect(
                                Collectors.toMap(
                                        ProcessorTransaction::processorTransactionId,
                                        Function.identity(),
                                        (first, ignored) -> first,
                                        LinkedHashMap::new));
        Map<BatchCurrency, List<ProcessorTransaction>> transactionsByBatch =
                uniqueProcessors.values().stream()
                        .collect(
                                Collectors.groupingBy(
                                        transaction ->
                                                new BatchCurrency(
                                                        transaction.settlementBatchId(),
                                                        transaction.currency()),
                                        LinkedHashMap::new,
                                        Collectors.toList()));

        List<Map.Entry<BatchCurrency, List<ProcessorTransaction>>> sortedBatches =
                new ArrayList<>(transactionsByBatch.entrySet());
        sortedBatches.sort(Comparator.comparing(entry -> entry.getKey().batchId()));

        List<BankSettlement> bankSettlements = new ArrayList<>();
        int bankSequence = 1;
        for (Map.Entry<BatchCurrency, List<ProcessorTransaction>> entry : sortedBatches) {
            String batchId = entry.getKey().batchId();
            if (batchId.startsWith("SYNTH-BATCH-MISSING-")) {
                continue;
            }
            List<ProcessorTransaction> transactions = entry.getValue();
            BigDecimal credit =
                    transactions.stream()
                            .map(ProcessorTransaction::netAmount)
                            .reduce(BigDecimal.ZERO.setScale(MoneyPolicy.SCALE), BigDecimal::add);
            Instant latestEvent =
                    transactions.stream()
                            .map(ProcessorTransaction::processorEventTime)
                            .max(Instant::compareTo)
                            .orElseThrow();
            Instant bookedAt =
                    batchId.startsWith("SYNTH-BATCH-LATE-")
                            ? latestEvent.plus(SETTLEMENT_WINDOW_DAYS + 4L, ChronoUnit.DAYS)
                            : latestEvent.plus(1, ChronoUnit.DAYS);
            if (batchId.startsWith("SYNTH-BATCH-AMOUNT-BREAK-")) {
                credit = credit.add(new BigDecimal("2.00"));
            }
            String bankCurrency =
                    batchId.startsWith("SYNTH-BATCH-BANK-CURRENCY-")
                            ? differentCurrency(entry.getKey().currency())
                            : entry.getKey().currency();
            BankSettlement settlement =
                    new BankSettlement(
                            "SYNTH-BANK-%06d".formatted(bankSequence++),
                            batchId,
                            bookedAt,
                            MoneyPolicy.scaled(credit),
                            bankCurrency,
                            "SYNTH-BANK-REF-%06d".formatted(bankSequence - 1),
                            "Synthetic settlement for " + batchId);
            bankSettlements.add(settlement);
            if (batchId.startsWith("SYNTH-BATCH-DUPLICATE-")) {
                bankSettlements.add(settlement);
            }
        }
        return new BankBuildResult(bankSettlements);
    }

    private int addBankOnlySettlements(
            GenerationRequest request, List<BankSettlement> bankSettlements) {
        int count =
                switch (request.scenario()) {
                    case MIXED_EXCEPTIONS -> rateCount(request.recordCount(), 5, 1_000);
                    case FAILURE_RECOVERY -> rateCount(request.recordCount(), 1, 1_000);
                    default -> 0;
                };
        Instant base = request.startDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        for (int index = 1; index <= count; index++) {
            bankSettlements.add(
                    new BankSettlement(
                            "SYNTH-BANK-ONLY-%06d".formatted(index),
                            "SYNTH-BATCH-BANK-ONLY-%06d".formatted(index),
                            base.plus(index, ChronoUnit.DAYS),
                            new BigDecimal("25.00"),
                            "USD",
                            "SYNTH-BANK-ONLY-REF-%06d".formatted(index),
                            "Synthetic bank-only entry with no processor batch"));
        }
        return count;
    }

    private static int rateCount(int records, int numerator, int denominator) {
        if (records < 1 || numerator < 1) {
            return 0;
        }
        return Math.max(1, Math.toIntExact((long) records * numerator / denominator));
    }

    private static void mergeCount(Map<String, Long> counts, String key, long amount) {
        if (amount > 0) {
            counts.merge(key, amount, Long::sum);
        }
    }

    private record CanonicalPayment(
            int sequence,
            String internalPaymentId,
            String merchantOrderId,
            Instant occurredAt,
            BigDecimal grossAmount,
            String currency,
            String syntheticCustomerReference,
            int daySpan) {}

    private record ProcessorOnlyRecord(ProcessorTransaction processorTransaction) {}

    private record BatchCurrency(String batchId, String currency) {}

    private record BankBuildResult(List<BankSettlement> bankSettlements) {}

    private enum InjectionKind {
        NONE(
                "",
                "MATCHED",
                "L1_EXACT_ORDER_MATCH",
                "Internal and processor records reconcile exactly."),
        MISSING_PROCESSOR(
                "missing_processor_records",
                "MISSING_PROCESSOR",
                "L1_MISSING_PROCESSOR",
                "The internal payment has no corresponding processor transaction."),
        DUPLICATE_INTERNAL(
                "duplicate_internal_records",
                "DUPLICATE_INTERNAL",
                "L1_DUPLICATE_INTERNAL",
                "The synthetic internal source repeats the same source identifier."),
        DUPLICATE_PROCESSOR(
                "duplicate_processor_records",
                "DUPLICATE_PROCESSOR",
                "L1_DUPLICATE_PROCESSOR",
                "The synthetic processor source repeats the same source identifier."),
        DUPLICATE_BANK_ENTRY(
                "duplicate_bank_entries",
                "DUPLICATE_BANK_ENTRY",
                "L2_DUPLICATE_BANK_DEPOSIT",
                "The synthetic bank source repeats the settlement entry."),
        AMBIGUOUS_MATCH(
                "ambiguous_matches",
                "AMBIGUOUS_MATCH",
                "L1_AMBIGUOUS_CARDINALITY",
                "The order maps to multiple distinct processor candidates."),
        AMOUNT_MISMATCH(
                "amount_mismatches",
                "AMOUNT_MISMATCH",
                "L1_GROSS_AMOUNT_EQUALITY",
                "The processor gross amount differs from the internal gross amount."),
        FEE_MISMATCH(
                "fee_mismatches",
                "FEE_MISMATCH",
                "L1_CONFIGURED_FEE_POLICY",
                "The processor fee differs from the configured synthetic fee policy."),
        CURRENCY_MISMATCH(
                "currency_mismatches",
                "CURRENCY_MISMATCH",
                "L1_CURRENCY_EQUALITY",
                "The processor and internal records use different currencies."),
        BANK_AMOUNT_MISMATCH(
                "bank_amount_mismatches",
                "AMOUNT_MISMATCH",
                "L2_SETTLEMENT_AMOUNT_EQUALITY",
                "The bank credit differs from the processor batch net total."),
        BANK_CURRENCY_MISMATCH(
                "bank_currency_mismatches",
                "CURRENCY_MISMATCH",
                "L2_CURRENCY_EQUALITY",
                "The bank entry currency differs from the processor batch currency."),
        LATE_SETTLEMENT(
                "late_settlements",
                "LATE_SETTLEMENT",
                "L2_SETTLEMENT_WINDOW",
                "The bank settlement is booked outside the configured settlement window."),
        MISSING_BANK_SETTLEMENT(
                "missing_bank_settlements",
                "MISSING_BANK_SETTLEMENT",
                "L2_MISSING_BANK_DEPOSIT",
                "The processor batch has no corresponding bank settlement."),
        FULL_REFUND(
                "full_refunds",
                "REFUND_MATCHED",
                "L1_FULL_REFUND_LINK",
                "The full synthetic refund is linked to its original captured payment."),
        PARTIAL_REFUND(
                "partial_refunds",
                "PARTIAL_REFUND",
                "L1_PARTIAL_REFUND_LINK",
                "The partial synthetic refund is linked to its original captured payment.");

        private final String manifestKey;
        private final String outcome;
        private final String reasonCode;
        private final String explanation;

        InjectionKind(String manifestKey, String outcome, String reasonCode, String explanation) {
            this.manifestKey = manifestKey;
            this.outcome = outcome;
            this.reasonCode = reasonCode;
            this.explanation = explanation;
        }

        String manifestKey() {
            return manifestKey;
        }

        String outcome() {
            return outcome;
        }

        String reasonCode() {
            return reasonCode;
        }

        String explanation() {
            return explanation;
        }
    }

    private record InjectionPlan(Map<Integer, InjectionKind> injectionBySequence) {
        static InjectionPlan create(GenerationRequest request) {
            List<Integer> available = new ArrayList<>(request.recordCount());
            for (int sequence = 1; sequence <= request.recordCount(); sequence++) {
                available.add(sequence);
            }
            Collections.shuffle(available, new java.util.Random(request.seed() ^ 0x4D49584544L));

            Map<InjectionKind, Integer> requested = requestedCounts(request);
            Map<Integer, InjectionKind> allocations = new HashMap<>();
            int cursor = 0;
            for (InjectionKind kind : InjectionKind.values()) {
                if (kind == InjectionKind.NONE) {
                    continue;
                }
                int count = Math.min(requested.getOrDefault(kind, 0), available.size() - cursor);
                for (int index = 0; index < count; index++) {
                    allocations.put(available.get(cursor++), kind);
                }
            }
            return new InjectionPlan(Collections.unmodifiableMap(allocations));
        }

        private static Map<InjectionKind, Integer> requestedCounts(GenerationRequest request) {
            EnumMap<InjectionKind, Integer> counts = new EnumMap<>(InjectionKind.class);
            int records = request.recordCount();
            switch (request.scenario()) {
                case CLEAN_SETTLEMENT, LOCAL_PERFORMANCE -> {}
                case MIXED_EXCEPTIONS -> {
                    counts.put(InjectionKind.MISSING_PROCESSOR, rateCount(records, 2, 100));
                    counts.put(InjectionKind.DUPLICATE_INTERNAL, rateCount(records, 1, 100));
                    counts.put(InjectionKind.DUPLICATE_PROCESSOR, rateCount(records, 2, 100));
                    counts.put(InjectionKind.DUPLICATE_BANK_ENTRY, rateCount(records, 1, 100));
                    counts.put(InjectionKind.AMBIGUOUS_MATCH, rateCount(records, 1, 100));
                    counts.put(InjectionKind.AMOUNT_MISMATCH, rateCount(records, 2, 100));
                    counts.put(InjectionKind.FEE_MISMATCH, rateCount(records, 2, 100));
                    counts.put(InjectionKind.CURRENCY_MISMATCH, rateCount(records, 1, 100));
                    counts.put(InjectionKind.BANK_AMOUNT_MISMATCH, rateCount(records, 1, 100));
                    counts.put(InjectionKind.BANK_CURRENCY_MISMATCH, rateCount(records, 1, 100));
                    counts.put(InjectionKind.LATE_SETTLEMENT, rateCount(records, 2, 100));
                    counts.put(InjectionKind.MISSING_BANK_SETTLEMENT, rateCount(records, 1, 100));
                    counts.put(InjectionKind.FULL_REFUND, rateCount(records, 2, 100));
                    counts.put(InjectionKind.PARTIAL_REFUND, rateCount(records, 2, 100));
                }
                case MONTH_END_CLOSE -> {
                    counts.put(InjectionKind.MISSING_PROCESSOR, rateCount(records, 3, 1_000));
                    counts.put(InjectionKind.AMOUNT_MISMATCH, rateCount(records, 3, 1_000));
                    counts.put(InjectionKind.FEE_MISMATCH, rateCount(records, 2, 1_000));
                    counts.put(InjectionKind.CURRENCY_MISMATCH, rateCount(records, 1, 1_000));
                    counts.put(InjectionKind.BANK_AMOUNT_MISMATCH, rateCount(records, 1, 1_000));
                    counts.put(InjectionKind.BANK_CURRENCY_MISMATCH, rateCount(records, 1, 1_000));
                    counts.put(InjectionKind.AMBIGUOUS_MATCH, rateCount(records, 1, 1_000));
                    counts.put(InjectionKind.LATE_SETTLEMENT, rateCount(records, 4, 1_000));
                    counts.put(InjectionKind.MISSING_BANK_SETTLEMENT, rateCount(records, 2, 1_000));
                    counts.put(InjectionKind.FULL_REFUND, rateCount(records, 1, 100));
                    counts.put(InjectionKind.PARTIAL_REFUND, rateCount(records, 1, 100));
                }
                case FAILURE_RECOVERY -> {
                    counts.put(InjectionKind.DUPLICATE_INTERNAL, rateCount(records, 1, 100));
                    counts.put(InjectionKind.DUPLICATE_PROCESSOR, rateCount(records, 1, 100));
                    counts.put(InjectionKind.DUPLICATE_BANK_ENTRY, rateCount(records, 5, 1_000));
                    counts.put(InjectionKind.AMBIGUOUS_MATCH, rateCount(records, 5, 1_000));
                    counts.put(InjectionKind.MISSING_PROCESSOR, rateCount(records, 5, 1_000));
                }
            }
            return counts;
        }

        InjectionKind injectionFor(int sequence) {
            return injectionBySequence.getOrDefault(sequence, InjectionKind.NONE);
        }

        Map<String, Long> actualCounts() {
            Map<String, Long> result = new TreeMap<>();
            injectionBySequence.values().forEach(kind -> mergeCount(result, kind.manifestKey(), 1));
            return result;
        }
    }
}
