package io.github.heybadrinath.reconriver.datasetgenerator;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.zip.ZipFile;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetGeneratorTest {
    private static final LocalDate START_DATE = LocalDate.of(2026, 1, 1);
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir Path temporaryDirectory;

    @Test
    void cleanScenarioReconcilesEveryCanonicalPayment() throws Exception {
        GeneratedScenario generated =
                generate(
                        ScenarioType.CLEAN_SETTLEMENT,
                        ScenarioType.CLEAN_SETTLEMENT.defaultRecordCount(),
                        42,
                        temporaryDirectory);

        List<CSVRecord> internal =
                csv(generated.scenarioDirectory().resolve("internal_transactions.csv"));
        List<CSVRecord> processors =
                csv(generated.scenarioDirectory().resolve("processor_transactions.csv"));
        List<CSVRecord> bank = csv(generated.scenarioDirectory().resolve("bank_settlements.csv"));
        List<CSVRecord> expected =
                csv(generated.scenarioDirectory().resolve("expected_reconciliation.csv"));
        JsonNode manifest = JSON.readTree(generated.manifest().toFile());

        assertThat(internal).hasSize(100);
        assertThat(processors).hasSize(100);
        List<CSVRecord> orderResults = scope(expected, "ORDER");
        List<CSVRecord> settlementResults = scope(expected, "SETTLEMENT");
        assertThat(orderResults)
                .hasSize(100)
                .allSatisfy(
                        row -> {
                            assertThat(row.get("expected_outcome")).isEqualTo("MATCHED");
                            assertThat(row.get("expected_reason_code"))
                                    .isEqualTo("L1_EXACT_ORDER_MATCH");
                            assertThat(row.get("expected_difference")).isEqualTo("0.00");
                            assertThat(row.get("work_key")).startsWith("SYNTH-ORDER-");
                            assertThat(row.get("settlement_batch_id")).isBlank();
                            assertThat(row.get("processor_transaction_id")).isNotBlank();
                            assertThat(row.get("bank_entry_id")).isBlank();
                        });
        assertThat(settlementResults)
                .hasSize(bank.size())
                .allSatisfy(
                        row -> {
                            assertThat(row.get("expected_outcome")).isEqualTo("MATCHED");
                            assertThat(row.get("expected_reason_code"))
                                    .isEqualTo("L2_EXACT_SETTLEMENT_MATCH");
                            assertThat(row.get("work_key"))
                                    .isEqualTo(row.get("settlement_batch_id"));
                            assertThat(row.get("internal_payment_id")).isBlank();
                            assertThat(row.get("processor_transaction_id")).isBlank();
                            assertThat(row.get("bank_entry_id")).isNotBlank();
                        });
        assertThat(manifest.path("canonical_payment_count").asInt()).isEqualTo(100);
        assertThat(manifest.path("injected_exception_counts").isEmpty()).isTrue();
        assertThat(manifest.path("expected_reconciliation_summary").path("MATCHED").asLong())
                .isEqualTo(expected.size());
        assertThat(manifest.at("/expected_reconciliation_summary_by_scope/ORDER/MATCHED").asLong())
                .isEqualTo(100);
        assertThat(
                        manifest.at("/expected_reconciliation_summary_by_scope/SETTLEMENT/MATCHED")
                                .asLong())
                .isEqualTo(bank.size());

        Map<String, BigDecimal> processorNetByBatch =
                processors.stream()
                        .collect(
                                Collectors.toMap(
                                        row -> row.get("settlement_batch_id"),
                                        row -> new BigDecimal(row.get("net_amount")),
                                        BigDecimal::add));
        Map<String, BigDecimal> bankCreditByBatch =
                bank.stream()
                        .collect(
                                Collectors.toMap(
                                        row -> row.get("settlement_batch_id"),
                                        row -> new BigDecimal(row.get("credited_amount"))));
        assertThat(bankCreditByBatch.keySet())
                .containsExactlyInAnyOrderElementsOf(processorNetByBatch.keySet());
        processorNetByBatch.forEach(
                (batchId, netAmount) ->
                        assertThat(bankCreditByBatch.get(batchId))
                                .as("bank credit for %s", batchId)
                                .isEqualByComparingTo(netAmount));
        assertThat(
                        processors.stream()
                                .collect(
                                        Collectors.groupingBy(
                                                row -> row.get("settlement_batch_id"),
                                                Collectors.counting()))
                                .values())
                .anySatisfy(count -> assertThat(count).isGreaterThan(1));
        assertThat(bank)
                .allSatisfy(
                        row -> {
                            assertThat(row.get("booked_at")).endsWith("Z");
                            assertThat(Instant.parse(row.get("booked_at"))).isNotNull();
                        });
    }

    @Test
    void processorMoneyAlwaysUsesExactDecimalArithmetic() throws Exception {
        GeneratedScenario generated =
                generate(ScenarioType.MONTH_END_CLOSE, 500, 8_675_309, temporaryDirectory);
        List<CSVRecord> processors =
                csv(generated.scenarioDirectory().resolve("processor_transactions.csv"));

        assertThat(processors)
                .isNotEmpty()
                .allSatisfy(
                        row -> {
                            BigDecimal gross = new BigDecimal(row.get("gross_amount"));
                            BigDecimal fee = new BigDecimal(row.get("fee_amount"));
                            BigDecimal net = new BigDecimal(row.get("net_amount"));
                            assertThat(gross.scale()).isEqualTo(2);
                            assertThat(fee.scale()).isEqualTo(2);
                            assertThat(net.scale()).isEqualTo(2);
                            assertThat(gross.subtract(fee)).isEqualByComparingTo(net);
                        });
    }

    @Test
    void sameConfigurationProducesByteIdenticalFilesAndZipAcrossTimeZones() throws Exception {
        Path firstRoot = temporaryDirectory.resolve("first");
        Path secondRoot = temporaryDirectory.resolve("second");
        TimeZone originalTimeZone = TimeZone.getDefault();
        GeneratedScenario first;
        GeneratedScenario second;
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Kolkata"));
            first = generate(ScenarioType.MIXED_EXCEPTIONS, 250, 91, firstRoot);
            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
            second = generate(ScenarioType.MIXED_EXCEPTIONS, 250, 91, secondRoot);
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }

        Map<String, byte[]> firstFiles = filesByName(first.scenarioDirectory());
        Map<String, byte[]> secondFiles = filesByName(second.scenarioDirectory());
        assertThat(secondFiles.keySet()).containsExactlyInAnyOrderElementsOf(firstFiles.keySet());
        firstFiles.forEach(
                (fileName, content) ->
                        assertThat(secondFiles.get(fileName))
                                .as("content of %s", fileName)
                                .isEqualTo(content));
    }

    @Test
    void changingSeedChangesLogicalSourceData() throws Exception {
        GeneratedScenario first =
                generate(
                        ScenarioType.CLEAN_SETTLEMENT,
                        50,
                        41,
                        temporaryDirectory.resolve("seed-41"));
        GeneratedScenario second =
                generate(
                        ScenarioType.CLEAN_SETTLEMENT,
                        50,
                        42,
                        temporaryDirectory.resolve("seed-42"));

        assertThat(
                        Files.readAllBytes(
                                first.scenarioDirectory().resolve("internal_transactions.csv")))
                .isNotEqualTo(
                        Files.readAllBytes(
                                second.scenarioDirectory().resolve("internal_transactions.csv")));
    }

    @Test
    void mixedScenarioInjectionCountsMatchThePhysicalRowsAndGroundTruth() throws Exception {
        GeneratedScenario generated =
                generate(ScenarioType.MIXED_EXCEPTIONS, 1_000, 42, temporaryDirectory);
        Path scenario = generated.scenarioDirectory();
        List<CSVRecord> internal = csv(scenario.resolve("internal_transactions.csv"));
        List<CSVRecord> processors = csv(scenario.resolve("processor_transactions.csv"));
        List<CSVRecord> expected = csv(scenario.resolve("expected_reconciliation.csv"));
        JsonNode manifest = JSON.readTree(generated.manifest().toFile());

        assertThat(manifest.at("/injected_exception_counts/missing_processor_records").asLong())
                .isEqualTo(20);
        assertThat(manifest.at("/injected_exception_counts/processor_only_records").asLong())
                .isEqualTo(20);
        assertThat(manifest.at("/injected_exception_counts/duplicate_processor_records").asLong())
                .isEqualTo(20);
        assertThat(manifest.at("/injected_exception_counts/malformed_rows").asLong()).isEqualTo(5);
        assertThat(manifest.at("/injected_exception_counts/duplicated_source_files").asLong())
                .isEqualTo(1);

        Map<String, Long> processorIdCounts =
                processors.stream()
                        .collect(
                                Collectors.groupingBy(
                                        row -> row.get("processor_transaction_id"),
                                        Collectors.counting()));
        assertThat(processorIdCounts.values().stream().filter(count -> count > 1).count())
                .isEqualTo(20);
        assertThat(
                        processors.stream()
                                .filter(row -> row.get("gross_amount").equals("NOT_A_NUMBER"))
                                .count())
                .isEqualTo(5);
        assertThat(Files.readAllBytes(scenario.resolve("internal_transactions_duplicate.csv")))
                .isEqualTo(Files.readAllBytes(scenario.resolve("internal_transactions.csv")));

        Set<String> sourceProcessorIds =
                processors.stream()
                        .map(row -> row.get("processor_transaction_id"))
                        .collect(Collectors.toSet());
        assertThat(
                        expected.stream()
                                .filter(row -> row.get("result_scope").equals("ORDER"))
                                .filter(
                                        row ->
                                                row.get("expected_outcome")
                                                        .equals("MISSING_PROCESSOR")))
                .hasSize(20)
                .allSatisfy(row -> assertThat(row.get("processor_transaction_id")).isBlank());
        assertThat(
                        expected.stream()
                                .filter(row -> row.get("result_scope").equals("ORDER"))
                                .filter(
                                        row ->
                                                row.get("expected_outcome")
                                                        .equals("MISSING_INTERNAL")))
                .hasSize(20)
                .allSatisfy(
                        row -> {
                            assertThat(row.get("internal_payment_id")).isBlank();
                            assertThat(sourceProcessorIds)
                                    .contains(row.get("processor_transaction_id"));
                        });
        assertThat(
                        expected.stream()
                                .filter(
                                        row ->
                                                Set.of("REFUND_MATCHED", "PARTIAL_REFUND")
                                                        .contains(row.get("expected_outcome"))))
                .hasSize(40)
                .allSatisfy(
                        row -> {
                            assertThat(row.get("result_scope")).isEqualTo("ORDER");
                            assertThat(row.get("processor_transaction_id")).endsWith("-REFUND");
                            assertThat(sourceProcessorIds)
                                    .contains(row.get("processor_transaction_id"));
                            assertThat(row.get("bank_entry_id")).isBlank();
                        });
        Map<String, CSVRecord> internalByOrder =
                internal.stream()
                        .collect(
                                Collectors.toMap(
                                        row -> row.get("merchant_order_id"),
                                        java.util.function.Function.identity(),
                                        (first, ignored) -> first));
        Map<String, CSVRecord> processorById =
                processors.stream()
                        .collect(
                                Collectors.toMap(
                                        row -> row.get("processor_transaction_id"),
                                        java.util.function.Function.identity(),
                                        (first, ignored) -> first));
        assertThat(
                        expected.stream()
                                .filter(
                                        row ->
                                                row.get("expected_outcome")
                                                        .equals("PARTIAL_REFUND")))
                .hasSize(20)
                .allSatisfy(
                        row -> {
                            BigDecimal gross =
                                    new BigDecimal(
                                            internalByOrder
                                                    .get(row.get("work_key"))
                                                    .get("gross_amount"));
                            BigDecimal refunded =
                                    new BigDecimal(
                                                    processorById
                                                            .get(
                                                                    row.get(
                                                                            "processor_transaction_id"))
                                                            .get("gross_amount"))
                                            .abs();
                            assertThat(new BigDecimal(row.get("expected_difference")))
                                    .isEqualByComparingTo(gross.subtract(refunded));
                        });
        assertThat(
                        expected.stream()
                                .filter(
                                        row ->
                                                row.get("expected_reason_code")
                                                        .equals("SOURCE_ROW_INVALID_AMOUNT")))
                .hasSize(5)
                .allSatisfy(
                        row -> {
                            assertThat(row.get("result_scope")).isEqualTo("ORDER");
                            assertThat(row.get("work_key"))
                                    .startsWith("INVALID:SYNTH-PROC-MALFORMED-ROWS-");
                            assertThat(row.get("expected_difference")).isBlank();
                        });
        assertThat(
                        expected.stream()
                                .filter(
                                        row ->
                                                row.get("expected_reason_code")
                                                        .equals("L2_BANK_WITHOUT_PROCESSOR_BATCH")))
                .hasSize(5)
                .allSatisfy(
                        row -> {
                            assertThat(row.get("work_key"))
                                    .startsWith("BANK_ONLY:SYNTH-BANK-ONLY-");
                            assertThat(new BigDecimal(row.get("expected_difference"))).isPositive();
                        });
        assertThat(
                        expected.stream()
                                .filter(
                                        row ->
                                                row.get("expected_reason_code")
                                                        .equals("L2_MISSING_BANK_DEPOSIT")))
                .hasSize(10)
                .allSatisfy(
                        row ->
                                assertThat(new BigDecimal(row.get("expected_difference")))
                                        .isNegative());
        assertThat(
                        scope(expected, "ORDER").stream()
                                .filter(
                                        row ->
                                                row.get("expected_outcome")
                                                        .equals("AMBIGUOUS_MATCH")))
                .hasSize(10)
                .allSatisfy(
                        row ->
                                assertThat(row.get("expected_reason_code"))
                                        .isEqualTo("L1_AMBIGUOUS_CARDINALITY"));
        assertThat(scope(expected, "SETTLEMENT"))
                .anySatisfy(
                        row ->
                                assertThat(row.get("expected_outcome"))
                                        .isIn(
                                                "DUPLICATE_BANK_ENTRY",
                                                "AMOUNT_MISMATCH",
                                                "CURRENCY_MISMATCH",
                                                "MISSING_PROCESSOR"));
        assertThat(internal).hasSize(1_010);
    }

    @Test
    void manifestChecksumsRowCountsAndOutcomeSummaryMatchGeneratedFiles() throws Exception {
        GeneratedScenario generated =
                generate(ScenarioType.MIXED_EXCEPTIONS, 300, 7, temporaryDirectory);
        JsonNode manifest = JSON.readTree(generated.manifest().toFile());

        manifest.path("file_sha256_checksums")
                .properties()
                .forEach(
                        entry -> {
                            Path file = generated.scenarioDirectory().resolve(entry.getKey());
                            assertThat(file).exists();
                            assertThat(sha256(file)).isEqualTo(entry.getValue().asText());
                        });
        manifest.path("file_row_counts")
                .properties()
                .forEach(
                        entry ->
                                assertThat(
                                                csvUnchecked(
                                                        generated
                                                                .scenarioDirectory()
                                                                .resolve(entry.getKey())))
                                        .as("row count for %s", entry.getKey())
                                        .hasSize(entry.getValue().asInt()));

        List<CSVRecord> groundTruth =
                csv(generated.scenarioDirectory().resolve("expected_reconciliation.csv"));
        assertThat(
                        groundTruth.stream()
                                .map(row -> row.get("result_scope") + "|" + row.get("work_key")))
                .doesNotHaveDuplicates();
        Map<String, Long> expectedSummary =
                groundTruth.stream()
                        .collect(
                                Collectors.groupingBy(
                                        row -> row.get("expected_outcome"), Collectors.counting()));
        assertThat(manifest.path("expected_reconciliation_summary").size())
                .isEqualTo(expectedSummary.size());
        assertThat(
                        manifest.path("expected_reconciliation_summary").properties().stream()
                                .mapToLong(entry -> entry.getValue().asLong())
                                .sum())
                .isEqualTo(expectedSummary.values().stream().mapToLong(Long::longValue).sum());
        manifest.path("expected_reconciliation_summary")
                .properties()
                .forEach(
                        entry ->
                                assertThat(expectedSummary.get(entry.getKey()))
                                        .as("ground-truth count for %s", entry.getKey())
                                        .isEqualTo(entry.getValue().asLong()));
        manifest.path("expected_reconciliation_summary_by_scope")
                .properties()
                .forEach(
                        scopeEntry ->
                                scopeEntry
                                        .getValue()
                                        .properties()
                                        .forEach(
                                                outcomeEntry ->
                                                        assertThat(
                                                                        csvUnchecked(
                                                                                        generated
                                                                                                .scenarioDirectory()
                                                                                                .resolve(
                                                                                                        "expected_reconciliation.csv"))
                                                                                .stream()
                                                                                .filter(
                                                                                        row ->
                                                                                                row.get(
                                                                                                                "result_scope")
                                                                                                        .equals(
                                                                                                                scopeEntry
                                                                                                                        .getKey()))
                                                                                .filter(
                                                                                        row ->
                                                                                                row.get(
                                                                                                                "expected_outcome")
                                                                                                        .equals(
                                                                                                                outcomeEntry
                                                                                                                        .getKey()))
                                                                                .count())
                                                                .as(
                                                                        "%s %s ground-truth count",
                                                                        scopeEntry.getKey(),
                                                                        outcomeEntry.getKey())
                                                                .isEqualTo(
                                                                        outcomeEntry
                                                                                .getValue()
                                                                                .asLong())));
        assertThat(
                        manifest
                                .path("expected_reconciliation_summary_by_scope")
                                .properties()
                                .stream()
                                .flatMap(scopeEntry -> scopeEntry.getValue().properties().stream())
                                .mapToLong(entry -> entry.getValue().asLong())
                                .sum())
                .isEqualTo(expectedSummary.values().stream().mapToLong(Long::longValue).sum());
    }

    @Test
    void monthEndAndPerformanceScenariosAggregateMultiplePaymentsPerBatch() {
        ScenarioData monthEnd = build(ScenarioType.MONTH_END_CLOSE, 3_100, 42);
        ScenarioData performance = build(ScenarioType.LOCAL_PERFORMANCE, 9_000, 42);

        assertAggregatedBatches(monthEnd);
        assertAggregatedBatches(performance);
    }

    @Test
    void failureScenarioDistinguishesInvalidAndQuarantinedGroundTruth() throws Exception {
        GeneratedScenario generated =
                generate(ScenarioType.FAILURE_RECOVERY, 1_000, 42, temporaryDirectory);
        List<CSVRecord> expected =
                csv(generated.scenarioDirectory().resolve("expected_reconciliation.csv"));
        JsonNode manifest = JSON.readTree(generated.manifest().toFile());

        assertThat(manifest.at("/injected_exception_counts/invalid_rows").asLong()).isEqualTo(5);
        assertThat(manifest.at("/injected_exception_counts/quarantined_rows").asLong())
                .isEqualTo(5);
        assertThat(manifest.at("/failure_recovery/controlled_failure_after_chunks").asInt())
                .isEqualTo(5);
        assertThat(manifest.at("/failure_recovery/controlled_failure_after_rows").asInt())
                .isEqualTo(
                        manifest.at("/failure_recovery/restart_chunk_size").asInt()
                                * manifest.at("/failure_recovery/controlled_failure_after_chunks")
                                        .asInt());
        assertThat(
                        expected.stream()
                                .filter(
                                        row ->
                                                row.get("expected_reason_code")
                                                        .equals("SOURCE_ROW_INVALID_TIMESTAMP")))
                .hasSize(5)
                .allSatisfy(
                        row -> {
                            assertThat(row.get("result_scope")).isEqualTo("ORDER");
                            assertThat(row.get("expected_difference")).isBlank();
                        });
        assertThat(
                        expected.stream()
                                .filter(
                                        row ->
                                                row.get("expected_reason_code")
                                                        .equals("SOURCE_ROW_INVALID_AMOUNT")))
                .hasSize(5)
                .allSatisfy(
                        row ->
                                assertThat(row.get("work_key"))
                                        .startsWith("INVALID:SYNTH-PROC-QUARANTINED-ROWS-"));
    }

    @Test
    void everyScenarioCreatesTheRequiredPackAndFailureScenarioDeclaresRestartContract()
            throws Exception {
        for (ScenarioType scenarioType : ScenarioType.values()) {
            GeneratedScenario generated =
                    generate(scenarioType, 80, 17, temporaryDirectory.resolve(scenarioType.slug()));
            assertThat(generated.scenarioDirectory()).isDirectory();
            assertThat(generated.manifest()).isRegularFile();
            assertThat(generated.zipArchive()).isRegularFile();
            assertThat(generated.scenarioDirectory())
                    .isDirectoryContaining(
                            path ->
                                    path.getFileName()
                                            .toString()
                                            .equals("internal_transactions.csv"));
            assertThat(generated.scenarioDirectory())
                    .isDirectoryContaining(
                            path ->
                                    path.getFileName()
                                            .toString()
                                            .equals("processor_transactions.csv"));
            assertThat(generated.scenarioDirectory())
                    .isDirectoryContaining(
                            path -> path.getFileName().toString().equals("bank_settlements.csv"));
            assertThat(generated.scenarioDirectory())
                    .isDirectoryContaining(
                            path ->
                                    path.getFileName()
                                            .toString()
                                            .equals("expected_reconciliation.csv"));
            assertThat(generated.scenarioDirectory())
                    .isDirectoryContaining(
                            path -> path.getFileName().toString().equals("README.md"));
        }

        Path failureManifest =
                temporaryDirectory
                        .resolve("failure-recovery")
                        .resolve("failure-recovery")
                        .resolve("scenario_manifest.json");
        JsonNode manifest = JSON.readTree(failureManifest.toFile());
        assertThat(manifest.at("/failure_recovery/controlled_failure_after_rows").asInt())
                .isEqualTo(20);
        assertThat(manifest.at("/failure_recovery/restart_chunk_size").asInt()).isEqualTo(4);
        assertThat(manifest.at("/failure_recovery/controlled_failure_after_chunks").asInt())
                .isEqualTo(5);
        assertThat(manifest.at("/failure_recovery/restart_expectation").asText())
                .contains("without creating duplicate reconciliation results");
    }

    @Test
    void monthEndManifestDocumentsTheAssumedExceptionRatesExplicitly() throws Exception {
        GeneratedScenario generated =
                generate(ScenarioType.MONTH_END_CLOSE, 10_000, 42, temporaryDirectory);
        JsonNode manifest = JSON.readTree(generated.manifest().toFile());

        assertThat(manifest.at("/assumed_exception_rates/missing_processor_records").asText())
                .isEqualTo("0.30%");
        assertThat(manifest.at("/assumed_exception_rates/full_refunds").asText())
                .isEqualTo("1.00%");
        assertThat(manifest.path("exception_percentages_assumption").asText())
                .contains("demonstration assumptions")
                .contains("not measurements");
    }

    @Test
    void zipContainsTheCompleteLooseScenarioWithoutRecursingIntoItself() throws Exception {
        GeneratedScenario generated =
                generate(ScenarioType.CLEAN_SETTLEMENT, 25, 42, temporaryDirectory);
        try (ZipFile zip = new ZipFile(generated.zipArchive().toFile(), StandardCharsets.UTF_8)) {
            Set<String> entries =
                    zip.stream().map(java.util.zip.ZipEntry::getName).collect(Collectors.toSet());
            assertThat(entries)
                    .containsExactlyInAnyOrder(
                            "README.md",
                            "bank_settlements.csv",
                            "expected_reconciliation.csv",
                            "internal_transactions.csv",
                            "processor_transactions.csv",
                            "scenario_manifest.json");
            assertThat(entries).doesNotContain("clean-settlement.zip");
            assertThat(zip.getInputStream(zip.getEntry("scenario_manifest.json")).readAllBytes())
                    .isEqualTo(Files.readAllBytes(generated.manifest()));
        }
    }

    private GeneratedScenario generate(ScenarioType type, int records, long seed, Path output) {
        return new DatasetGenerator()
                .generate(new GenerationRequest(type, records, seed, START_DATE, output));
    }

    private ScenarioData build(ScenarioType type, int records, long seed) {
        return new DatasetGenerator()
                .buildScenario(
                        new GenerationRequest(
                                type,
                                records,
                                seed,
                                START_DATE,
                                temporaryDirectory.resolve("build-" + type.slug())));
    }

    private List<CSVRecord> scope(List<CSVRecord> rows, String resultScope) {
        return rows.stream().filter(row -> row.get("result_scope").equals(resultScope)).toList();
    }

    private void assertAggregatedBatches(ScenarioData data) {
        Map<String, Long> counts =
                data.processorTransactions().stream()
                        .filter(
                                transaction ->
                                        transaction
                                                        .settlementBatchId()
                                                        .startsWith("SYNTH-BATCH-USD-")
                                                || transaction
                                                        .settlementBatchId()
                                                        .startsWith("SYNTH-BATCH-EUR-")
                                                || transaction
                                                        .settlementBatchId()
                                                        .startsWith("SYNTH-BATCH-GBP-"))
                        .collect(
                                Collectors.groupingBy(
                                        ProcessorTransaction::settlementBatchId,
                                        Collectors.counting()));
        assertThat(counts).isNotEmpty();
        assertThat(counts.values())
                .anySatisfy(count -> assertThat(count).isGreaterThanOrEqualTo(10));
        assertThat(counts.size()).isLessThan(data.processorTransactions().size() / 2);
    }

    private List<CSVRecord> csv(Path path) throws IOException {
        try (CSVParser parser =
                CSVParser.parse(
                        path,
                        StandardCharsets.UTF_8,
                        CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).get())) {
            return parser.getRecords();
        }
    }

    private List<CSVRecord> csvUnchecked(Path path) {
        try {
            return csv(path);
        } catch (IOException exception) {
            throw new AssertionError(exception);
        }
    }

    private Map<String, byte[]> filesByName(Path directory) throws IOException {
        try (var paths = Files.list(directory)) {
            Map<String, byte[]> result = new HashMap<>();
            for (Path path : paths.toList()) {
                result.put(path.getFileName().toString(), Files.readAllBytes(path));
            }
            return result;
        }
    }

    private String sha256(Path path) {
        try {
            return java.util.HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
