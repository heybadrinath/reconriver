package io.github.heybadrinath.reconriver.datasetgenerator;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

final class ScenarioPackWriter {
    private static final int SETTLEMENT_WINDOW_DAYS = 3;
    static final String INTERNAL_FILE = "internal_transactions.csv";
    static final String PROCESSOR_FILE = "processor_transactions.csv";
    static final String BANK_FILE = "bank_settlements.csv";
    static final String EXPECTED_FILE = "expected_reconciliation.csv";
    static final String MANIFEST_FILE = "scenario_manifest.json";
    static final String README_FILE = "README.md";
    static final String DUPLICATE_INTERNAL_FILE = "internal_transactions_duplicate.csv";
    static final String GENERATOR_VERSION = "1.1.0";

    private static final String[] INTERNAL_HEADER = {
        "internal_payment_id",
        "merchant_order_id",
        "occurred_at",
        "gross_amount",
        "currency",
        "payment_status",
        "payment_method",
        "synthetic_customer_reference"
    };
    private static final String[] PROCESSOR_HEADER = {
        "processor_transaction_id",
        "merchant_order_id",
        "processor_event_type",
        "processor_event_time",
        "gross_amount",
        "fee_amount",
        "net_amount",
        "currency",
        "settlement_batch_id",
        "processor_status"
    };
    private static final String[] BANK_HEADER = {
        "bank_entry_id",
        "settlement_batch_id",
        "booked_at",
        "credited_amount",
        "currency",
        "bank_reference",
        "description"
    };
    private static final String[] EXPECTED_HEADER = {
        "scenario_id",
        "result_scope",
        "work_key",
        "settlement_batch_id",
        "internal_payment_id",
        "processor_transaction_id",
        "bank_entry_id",
        "expected_outcome",
        "expected_reason_code",
        "expected_difference",
        "explanation"
    };

    GeneratedScenario write(ScenarioData data) {
        try {
            Path scenarioDirectory =
                    data.request()
                            .outputDirectory()
                            .toAbsolutePath()
                            .normalize()
                            .resolve(data.request().scenario().slug());
            Files.createDirectories(scenarioDirectory);

            writeInternal(scenarioDirectory.resolve(INTERNAL_FILE), data.internalTransactions());
            writeProcessor(
                    scenarioDirectory.resolve(PROCESSOR_FILE),
                    data.processorTransactions(),
                    data.malformedProcessorRows());
            writeBank(scenarioDirectory.resolve(BANK_FILE), data.bankSettlements());
            writeExpected(scenarioDirectory.resolve(EXPECTED_FILE), data.expectedReconciliations());

            Path duplicateSource = scenarioDirectory.resolve(DUPLICATE_INTERNAL_FILE);
            if (data.duplicatedSourceFile()) {
                Files.copy(
                        scenarioDirectory.resolve(INTERNAL_FILE),
                        duplicateSource,
                        StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(duplicateSource);
            }

            Files.writeString(
                    scenarioDirectory.resolve(README_FILE),
                    readme(data),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING);

            List<String> checksumFiles =
                    new ArrayList<>(
                            List.of(
                                    INTERNAL_FILE,
                                    PROCESSOR_FILE,
                                    BANK_FILE,
                                    EXPECTED_FILE,
                                    README_FILE));
            if (data.duplicatedSourceFile()) {
                checksumFiles.add(DUPLICATE_INTERNAL_FILE);
            }
            checksumFiles.sort(String::compareTo);

            Map<String, String> checksums = new LinkedHashMap<>();
            for (String fileName : checksumFiles) {
                checksums.put(fileName, sha256(scenarioDirectory.resolve(fileName)));
            }

            ScenarioManifest manifest = manifest(data, checksums);
            Path manifestPath = scenarioDirectory.resolve(MANIFEST_FILE);
            writeManifest(manifestPath, manifest);

            List<String> archiveFiles = new ArrayList<>(checksumFiles);
            archiveFiles.add(MANIFEST_FILE);
            archiveFiles.sort(String::compareTo);
            Path zipPath = scenarioDirectory.resolve(data.request().scenario().slug() + ".zip");
            writeZip(
                    zipPath,
                    scenarioDirectory,
                    archiveFiles,
                    data.request().startDate().atStartOfDay(ZoneOffset.UTC).toInstant());
            return new GeneratedScenario(scenarioDirectory, manifestPath, zipPath);
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to write generated scenario", exception);
        }
    }

    private void writeInternal(Path path, List<InternalTransaction> transactions)
            throws IOException {
        writeCsv(
                path,
                INTERNAL_HEADER,
                printer ->
                        transactions.forEach(
                                transaction ->
                                        print(
                                                printer,
                                                transaction.internalPaymentId(),
                                                transaction.merchantOrderId(),
                                                transaction.occurredAt(),
                                                money(transaction.grossAmount()),
                                                transaction.currency(),
                                                transaction.paymentStatus(),
                                                transaction.paymentMethod(),
                                                transaction.syntheticCustomerReference())));
    }

    private void writeProcessor(
            Path path,
            List<ProcessorTransaction> transactions,
            List<MalformedProcessorRow> malformedRows)
            throws IOException {
        writeCsv(
                path,
                PROCESSOR_HEADER,
                printer -> {
                    transactions.forEach(
                            transaction ->
                                    print(
                                            printer,
                                            transaction.processorTransactionId(),
                                            transaction.merchantOrderId(),
                                            transaction.processorEventType(),
                                            transaction.processorEventTime(),
                                            money(transaction.grossAmount()),
                                            money(transaction.feeAmount()),
                                            money(transaction.netAmount()),
                                            transaction.currency(),
                                            transaction.settlementBatchId(),
                                            transaction.processorStatus()));
                    malformedRows.forEach(
                            row ->
                                    print(
                                            printer,
                                            row.processorTransactionId(),
                                            row.merchantOrderId(),
                                            row.processorEventType(),
                                            row.processorEventTime(),
                                            row.grossAmount(),
                                            row.feeAmount(),
                                            row.netAmount(),
                                            row.currency(),
                                            row.settlementBatchId(),
                                            row.processorStatus()));
                });
    }

    private void writeBank(Path path, List<BankSettlement> settlements) throws IOException {
        writeCsv(
                path,
                BANK_HEADER,
                printer ->
                        settlements.forEach(
                                settlement ->
                                        print(
                                                printer,
                                                settlement.bankEntryId(),
                                                settlement.settlementBatchId(),
                                                settlement.bookedAt(),
                                                money(settlement.creditedAmount()),
                                                settlement.currency(),
                                                settlement.bankReference(),
                                                settlement.description())));
    }

    private void writeExpected(Path path, List<ExpectedReconciliation> reconciliations)
            throws IOException {
        writeCsv(
                path,
                EXPECTED_HEADER,
                printer ->
                        reconciliations.forEach(
                                reconciliation ->
                                        print(
                                                printer,
                                                reconciliation.scenarioId(),
                                                reconciliation.resultScope(),
                                                reconciliation.workKey(),
                                                reconciliation.settlementBatchId(),
                                                reconciliation.internalPaymentId(),
                                                reconciliation.processorTransactionId(),
                                                reconciliation.bankEntryId(),
                                                reconciliation.expectedOutcome(),
                                                reconciliation.expectedReasonCode(),
                                                money(reconciliation.expectedDifference()),
                                                reconciliation.explanation())));
    }

    private void writeCsv(Path path, String[] header, CsvBody body) throws IOException {
        CSVFormat format =
                CSVFormat.DEFAULT.builder().setHeader(header).setRecordSeparator('\n').get();
        try (BufferedWriter writer =
                        Files.newBufferedWriter(
                                path,
                                StandardCharsets.UTF_8,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING);
                CSVPrinter printer = new CSVPrinter(writer, format)) {
            body.write(printer);
        }
    }

    private void print(CSVPrinter printer, Object... values) {
        try {
            printer.printRecord(values);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    private String money(BigDecimal value) {
        return value == null ? "" : MoneyPolicy.scaled(value).toPlainString();
    }

    private ScenarioManifest manifest(ScenarioData data, Map<String, String> checksums) {
        Map<String, Long> fileRows = new TreeMap<>();
        fileRows.put(INTERNAL_FILE, (long) data.internalTransactions().size());
        fileRows.put(
                PROCESSOR_FILE,
                (long) data.processorTransactions().size() + data.malformedProcessorRows().size());
        fileRows.put(BANK_FILE, (long) data.bankSettlements().size());
        fileRows.put(EXPECTED_FILE, (long) data.expectedReconciliations().size());
        if (data.duplicatedSourceFile()) {
            fileRows.put(DUPLICATE_INTERNAL_FILE, (long) data.internalTransactions().size());
        }

        Map<String, Long> summary =
                data.expectedReconciliations().stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        ExpectedReconciliation::expectedOutcome,
                                        TreeMap::new,
                                        java.util.stream.Collectors.counting()));
        Map<String, Map<String, Long>> summaryByScope =
                data.expectedReconciliations().stream()
                        .collect(
                                java.util.stream.Collectors.groupingBy(
                                        ExpectedReconciliation::resultScope,
                                        TreeMap::new,
                                        java.util.stream.Collectors.groupingBy(
                                                ExpectedReconciliation::expectedOutcome,
                                                TreeMap::new,
                                                java.util.stream.Collectors.counting())));
        Set<String> currencies = new TreeSet<>();
        data.internalTransactions().forEach(transaction -> currencies.add(transaction.currency()));
        data.processorTransactions().forEach(transaction -> currencies.add(transaction.currency()));
        data.bankSettlements().forEach(settlement -> currencies.add(settlement.currency()));

        FailureRecoveryManifest failureRecovery =
                data.failureRecoveryControls() == null
                        ? null
                        : new FailureRecoveryManifest(
                                data.failureRecoveryControls().controlledFailureAfterRows(),
                                data.failureRecoveryControls().restartChunkSize(),
                                data.failureRecoveryControls().controlledFailureAfterChunks(),
                                data.failureRecoveryControls().restartExpectation());
        return new ScenarioManifest(
                data.request().scenario().slug(),
                data.request().scenario().displayName(),
                GENERATOR_VERSION,
                data.request().seed(),
                data.request().startDate(),
                data.request().startDate().atStartOfDay(ZoneOffset.UTC).toInstant(),
                data.request().recordCount(),
                fileRows,
                new TreeMap<>(data.injectedCounts()),
                assumedExceptionRates(data),
                new FeePolicyManifest("2.90%", "0.30", "HALF_UP", "gross_amount - fee_amount"),
                SETTLEMENT_WINDOW_DAYS
                        + " calendar days after the latest processor event in a batch",
                List.copyOf(currencies),
                checksums,
                "Checksums cover every loose scenario file except this manifest and the ZIP archive, "
                        + "which would otherwise require recursive checksums.",
                summary,
                summaryByScope,
                "This dataset is entirely synthetic. It contains no genuine people, payments, cards, "
                        + "bank accounts, addresses, or customer identifiers.",
                "All exception percentages and distributions are documented demonstration assumptions; "
                        + "they are not measurements of real payment behavior.",
                failureRecovery);
    }

    private Map<String, String> assumedExceptionRates(ScenarioData data) {
        Map<String, String> rates = new TreeMap<>();
        BigDecimal canonicalCount = BigDecimal.valueOf(data.request().recordCount());
        data.injectedCounts()
                .forEach(
                        (condition, count) -> {
                            if (!"duplicated_source_files".equals(condition)) {
                                String percentage =
                                        BigDecimal.valueOf(count)
                                                        .multiply(BigDecimal.valueOf(100))
                                                        .divide(
                                                                canonicalCount,
                                                                2,
                                                                java.math.RoundingMode.HALF_UP)
                                                        .toPlainString()
                                                + "%";
                                rates.put(condition, percentage);
                            }
                        });
        return rates;
    }

    private String readme(ScenarioData data) {
        StringBuilder builder = new StringBuilder();
        builder.append("# ")
                .append(data.request().scenario().displayName())
                .append(" scenario\n\n")
                .append("This pack was generated by the ReconRiver Java dataset generator. ")
                .append(
                        "It is deterministic for the recorded scenario, record count, seed, and start date.\n\n")
                .append("## Configuration\n\n")
                .append("- Scenario ID: `")
                .append(data.request().scenario().slug())
                .append("`\n")
                .append("- Canonical payments: ")
                .append(data.request().recordCount())
                .append("\n")
                .append("- Random seed: ")
                .append(data.request().seed())
                .append("\n")
                .append("- Fixed start date: ")
                .append(data.request().startDate())
                .append("\n")
                .append("- Fee policy: 2.90% of positive gross amount plus 0.30, rounded HALF_UP\n")
                .append("- Settlement window: ")
                .append(SETTLEMENT_WINDOW_DAYS)
                .append(" calendar days after the latest processor event in a batch\n\n")
                .append("## Files\n\n")
                .append("- `internal_transactions.csv`: synthetic internal ledger\n")
                .append("- `processor_transactions.csv`: derived processor events\n")
                .append(
                        "- `bank_settlements.csv`: settlement batches derived from processor net amounts\n")
                .append(
                        "- `expected_reconciliation.csv`: deterministic ORDER and SETTLEMENT ground truth\n")
                .append(
                        "- `scenario_manifest.json`: configuration, counts, checksums, and outcome summary\n");
        if (data.duplicatedSourceFile()) {
            builder.append(
                    "- `internal_transactions_duplicate.csv`: byte-identical duplicate source used to test idempotency\n");
        }
        builder.append("\n## Ground-truth identity\n\n")
                .append(
                        "`expected_reconciliation.csv` contains separate `ORDER` and `SETTLEMENT` results. ")
                .append("Use `result_scope` plus `work_key` as the stable logical identity. ")
                .append(
                        "Normal order keys are merchant order IDs; normal settlement keys are external batch IDs. ")
                .append(
                        "Invalid-row and bank-only keys use explicit synthetic prefixes. Blank monetary differences represent null, not zero.\n");
        builder.append("\n## Injected conditions\n\n");
        if (data.injectedCounts().isEmpty()) {
            builder.append(
                    "No intentional exceptions are injected. Every canonical payment should reconcile.\n");
        } else {
            Map<String, String> assumedRates = assumedExceptionRates(data);
            data.injectedCounts().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(
                            entry ->
                                    builder.append("- ")
                                            .append(entry.getKey().replace('_', ' '))
                                            .append(": ")
                                            .append(entry.getValue())
                                            .append(
                                                    assumedRates.containsKey(entry.getKey())
                                                            ? " ("
                                                                    + assumedRates.get(
                                                                            entry.getKey())
                                                                    + " of canonical payment count)"
                                                            : "")
                                            .append("\n"));
        }
        if (data.failureRecoveryControls() != null) {
            builder.append("\n## Failure and restart contract\n\n")
                    .append("The importing batch job should fail in a controlled way after ")
                    .append(data.failureRecoveryControls().controlledFailureAfterRows())
                    .append(" processed rows (after ")
                    .append(data.failureRecoveryControls().controlledFailureAfterChunks())
                    .append(" committed chunks), persist chunks of ")
                    .append(data.failureRecoveryControls().restartChunkSize())
                    .append(
                            " rows, and restart from the last committed chunk without duplicate results.\n");
        }
        builder.append("\n## Safety and limitations\n\n")
                .append(
                        "All references are visibly synthetic. Do not treat exception rates as real-world statistics. ")
                .append(
                        "This dataset contains no genuine personal, payment, card, or bank information.\n\n")
                .append(
                        "Dataset licence: Creative Commons Attribution 4.0 International (CC BY 4.0).\n");
        return builder.toString();
    }

    private void writeManifest(Path path, ScenarioManifest manifest) throws IOException {
        ObjectMapper mapper =
                JsonMapper.builder()
                        .addModule(new JavaTimeModule())
                        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                        .build();
        byte[] json = mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(manifest);
        try (OutputStream output =
                Files.newOutputStream(
                        path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            output.write(json);
            output.write('\n');
        }
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8_192];
                int bytesRead;
                while ((bytesRead = input.read(buffer)) != -1) {
                    digest.update(buffer, 0, bytesRead);
                }
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", exception);
        }
    }

    private void writeZip(
            Path zipPath, Path scenarioDirectory, List<String> files, Instant fixedTime)
            throws IOException {
        LocalDateTime zipTimestamp = LocalDateTime.ofInstant(fixedTime, ZoneOffset.UTC);
        try (ZipOutputStream zip =
                new ZipOutputStream(
                        Files.newOutputStream(
                                zipPath,
                                StandardOpenOption.CREATE,
                                StandardOpenOption.TRUNCATE_EXISTING),
                        StandardCharsets.UTF_8)) {
            for (String fileName : files) {
                ZipEntry entry = new ZipEntry(fileName);
                entry.setTimeLocal(zipTimestamp);
                zip.putNextEntry(entry);
                Files.copy(scenarioDirectory.resolve(fileName), zip);
                zip.closeEntry();
            }
        }
    }

    @FunctionalInterface
    private interface CsvBody {
        void write(CSVPrinter printer);
    }

    private record FeePolicyManifest(
            String percentageRate,
            String fixedCharge,
            String roundingMode,
            String netAmountFormula) {}

    private record FailureRecoveryManifest(
            int controlledFailureAfterRows,
            int restartChunkSize,
            int controlledFailureAfterChunks,
            String restartExpectation) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    private record ScenarioManifest(
            String scenarioId,
            String scenarioName,
            String generatorVersion,
            long randomSeed,
            java.time.LocalDate fixedStartDate,
            Instant generationTimestamp,
            int canonicalPaymentCount,
            Map<String, Long> fileRowCounts,
            Map<String, Long> injectedExceptionCounts,
            Map<String, String> assumedExceptionRates,
            FeePolicyManifest feePolicy,
            String settlementWindow,
            List<String> currencies,
            Map<String, String> fileSha256Checksums,
            String checksumScope,
            Map<String, Long> expectedReconciliationSummary,
            Map<String, Map<String, Long>> expectedReconciliationSummaryByScope,
            String syntheticDataDisclaimer,
            String exceptionPercentagesAssumption,
            FailureRecoveryManifest failureRecovery) {}
}
