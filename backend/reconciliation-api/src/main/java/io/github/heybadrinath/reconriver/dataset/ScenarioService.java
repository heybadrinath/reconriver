package io.github.heybadrinath.reconriver.dataset;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.heybadrinath.reconriver.ingestion.ImportService;
import io.github.heybadrinath.reconriver.ingestion.ImportView;
import io.github.heybadrinath.reconriver.ingestion.SourceType;
import io.github.heybadrinath.reconriver.shared.api.ApiException;
import io.swagger.v3.oas.annotations.media.Schema;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class ScenarioService {
    private static final String RESOURCE_ROOT = "demo-scenarios/";
    private static final String DISCLAIMER =
            "Synthetic demonstration data only. Never upload real payment, customer, card or bank information.";
    private static final List<ScenarioDefinition> DEFINITIONS =
            List.of(
                    new ScenarioDefinition(
                            "clean-settlement",
                            "A baseline in which every payment and settlement matches."),
                    new ScenarioDefinition(
                            "mixed-exceptions",
                            "A compact investigation queue covering common payment and settlement discrepancies."),
                    new ScenarioDefinition(
                            "month-end-close",
                            "A multi-day close with several batches, refunds and documented exception assumptions."),
                    new ScenarioDefinition(
                            "failure-recovery",
                            "Duplicate and invalid rows for demonstrating quarantine and restart safety."));
    private static final Set<String> ALWAYS_DOWNLOADABLE_NAMES =
            Set.of(
                    "internal_transactions.csv",
                    "processor_transactions.csv",
                    "bank_settlements.csv",
                    "expected_reconciliation.csv",
                    "scenario_manifest.json",
                    "README.md");

    private final ImportService imports;
    private final ObjectMapper objectMapper;

    ScenarioService(ImportService imports, ObjectMapper objectMapper) {
        this.imports = imports;
        this.objectMapper = objectMapper;
    }

    public ListResponse list() {
        return new ListResponse(DEFINITIONS.stream().map(this::view).toList());
    }

    public ScenarioView get(String slug) {
        return view(definition(slug));
    }

    public ScenarioFile download(String slug, String fileName) {
        ScenarioDefinition definition = definition(slug);
        String archiveName = definition.slug() + ".zip";
        boolean manifestFile = manifest(definition.slug()).path("file_row_counts").has(fileName);
        if (!ALWAYS_DOWNLOADABLE_NAMES.contains(fileName)
                && !manifestFile
                && !archiveName.equals(fileName)) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "SCENARIO_FILE_NOT_FOUND",
                    "That file is not part of the selected scenario.");
        }
        return new ScenarioFile(fileName, contentType(fileName), read(definition.slug(), fileName));
    }

    public LoadResponse load(UUID workspaceId, String slug) {
        ScenarioDefinition definition = definition(slug);
        List<ImportView> staged =
                List.of(
                        imports.stageGenerated(
                                workspaceId,
                                SourceType.INTERNAL,
                                "internal_transactions.csv",
                                read(definition.slug(), "internal_transactions.csv")),
                        imports.stageGenerated(
                                workspaceId,
                                SourceType.PROCESSOR,
                                "processor_transactions.csv",
                                read(definition.slug(), "processor_transactions.csv")),
                        imports.stageGenerated(
                                workspaceId,
                                SourceType.BANK,
                                "bank_settlements.csv",
                                read(definition.slug(), "bank_settlements.csv")));
        Integer controlledFailureAfterChunks =
                controlledFailureAfterChunks(manifest(definition.slug()));
        return new LoadResponse(
                workspaceId,
                slug,
                staged,
                controlledFailureAfterChunks,
                "The canonical scenario files were queued for chunk-based ingestion. Wait for all three imports to finish before starting reconciliation.");
    }

    private ScenarioView view(ScenarioDefinition definition) {
        JsonNode manifest = manifest(definition.slug());
        Map<String, Integer> expectedSummary =
                integerMap(manifest.path("expected_reconciliation_summary"));
        int expectedExceptions =
                expectedSummary.entrySet().stream()
                        .filter(
                                entry ->
                                        !entry.getKey().equals("MATCHED")
                                                && !entry.getKey().equals("REFUND_MATCHED"))
                        .mapToInt(Map.Entry::getValue)
                        .sum();
        Map<String, Integer> rowCounts = integerMap(manifest.path("file_row_counts"));
        Map<String, String> checksums = stringMap(manifest.path("file_sha256_checksums"));
        List<FileView> files = new ArrayList<>();
        rowCounts.forEach(
                (name, rows) ->
                        files.add(
                                fileFor(
                                        definition.slug(),
                                        name,
                                        sourceType(name),
                                        rows,
                                        checksums.get(name))));
        files.add(fileFor(definition.slug(), "scenario_manifest.json", null, 1, null));
        files.add(fileFor(definition.slug(), "README.md", null, 0, checksums.get("README.md")));
        files.add(fileFor(definition.slug(), definition.slug() + ".zip", null, 0, null));
        return new ScenarioView(
                definition.slug(),
                manifest.path("scenario_name").asText(),
                definition.purpose(),
                definition.purpose(),
                manifest.path("canonical_payment_count").asInt(),
                expectedExceptions,
                controlledFailureAfterChunks(manifest),
                List.copyOf(files),
                expectedSummary,
                DISCLAIMER);
    }

    private ScenarioDefinition definition(String slug) {
        return DEFINITIONS.stream()
                .filter(candidate -> candidate.slug().equals(slug))
                .findFirst()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "SCENARIO_NOT_FOUND",
                                        "No demonstration scenario exists with that slug."));
    }

    private JsonNode manifest(String slug) {
        try {
            return objectMapper.readTree(read(slug, "scenario_manifest.json"));
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "The packaged scenario manifest is invalid: " + slug, exception);
        }
    }

    private byte[] read(String slug, String fileName) {
        ClassPathResource resource = new ClassPathResource(RESOURCE_ROOT + slug + '/' + fileName);
        if (!resource.exists()) {
            throw new IllegalStateException(
                    "The canonical scenario resource is missing: " + slug + '/' + fileName);
        }
        try (var input = resource.getInputStream()) {
            return input.readAllBytes();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "The canonical scenario resource could not be read: " + slug + '/' + fileName,
                    exception);
        }
    }

    private FileView fileFor(String slug, String name, SourceType type, int rows, String checksum) {
        return new FileView(
                name, type, rows, checksum, "/api/v1/demo/scenarios/" + slug + "/files/" + name);
    }

    private SourceType sourceType(String name) {
        return switch (name) {
            case "internal_transactions.csv" -> SourceType.INTERNAL;
            case "processor_transactions.csv" -> SourceType.PROCESSOR;
            case "bank_settlements.csv" -> SourceType.BANK;
            default -> null;
        };
    }

    private String contentType(String fileName) {
        if (fileName.endsWith(".csv")) {
            return "text/csv";
        }
        if (fileName.endsWith(".json")) {
            return "application/json";
        }
        if (fileName.endsWith(".zip")) {
            return "application/zip";
        }
        return "text/markdown";
    }

    private Map<String, Integer> integerMap(JsonNode node) {
        LinkedHashMap<String, Integer> values = new LinkedHashMap<>();
        node.properties().forEach(entry -> values.put(entry.getKey(), entry.getValue().asInt()));
        return Map.copyOf(values);
    }

    private Map<String, String> stringMap(JsonNode node) {
        LinkedHashMap<String, String> values = new LinkedHashMap<>();
        node.properties().forEach(entry -> values.put(entry.getKey(), entry.getValue().asText()));
        return Map.copyOf(values);
    }

    private Integer nullableInteger(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        return value.isIntegralNumber() ? value.asInt() : null;
    }

    private Integer controlledFailureAfterChunks(JsonNode manifest) {
        return nullableInteger(
                manifest.path("failure_recovery"), "controlled_failure_after_chunks");
    }

    public record ListResponse(List<ScenarioView> items) {}

    public record ScenarioView(
            String slug,
            String name,
            String purpose,
            String description,
            int canonicalPaymentCount,
            int expectedExceptionCount,
            Integer controlledFailureAfterChunks,
            List<FileView> availableFiles,
            Map<String, Integer> expectedReconciliationSummary,
            String warning) {}

    public record FileView(
            String fileName,
            @Schema(nullable = true) SourceType sourceType,
            int rowCount,
            @Schema(nullable = true) String checksum,
            String downloadUrl) {}

    public record ScenarioFile(String fileName, String contentType, byte[] content) {}

    public record LoadResponse(
            UUID workspaceId,
            String scenarioSlug,
            List<ImportView> imports,
            Integer controlledFailureAfterChunks,
            String message) {}

    private record ScenarioDefinition(String slug, String purpose) {}
}
