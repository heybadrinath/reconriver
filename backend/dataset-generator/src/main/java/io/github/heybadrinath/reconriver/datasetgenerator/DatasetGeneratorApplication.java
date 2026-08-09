package io.github.heybadrinath.reconriver.datasetgenerator;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.ParameterException;

@Command(
        name = "reconriver-dataset-generator",
        description = "Generate deterministic synthetic payment-reconciliation scenario packs.",
        mixinStandardHelpOptions = true,
        version = "ReconRiver dataset generator 1.1.0")
public final class DatasetGeneratorApplication implements Callable<Integer> {
    @Option(
            names = "--scenario",
            required = true,
            paramLabel = "SLUG",
            description =
                    "Scenario: clean-settlement, mixed-exceptions, month-end-close, failure-recovery, or local-performance.")
    private String scenarioSlug;

    @Option(
            names = "--records",
            paramLabel = "COUNT",
            description = "Override the scenario's canonical payment count.")
    private Integer recordCount;

    @Option(
            names = "--seed",
            defaultValue = "42",
            paramLabel = "LONG",
            description = "Deterministic random seed. Default: ${DEFAULT-VALUE}.")
    private long seed;

    @Option(
            names = "--start-date",
            defaultValue = "2026-01-01",
            paramLabel = "YYYY-MM-DD",
            description = "Fixed UTC start date. Default: ${DEFAULT-VALUE}.")
    private LocalDate startDate;

    @Option(
            names = "--output",
            defaultValue = "../datasets/generated",
            paramLabel = "DIRECTORY",
            description = "Parent directory for scenario packs. Default: ${DEFAULT-VALUE}.")
    private Path outputDirectory;

    @Override
    public Integer call() {
        ScenarioType scenario;
        try {
            scenario = ScenarioType.fromSlug(scenarioSlug);
        } catch (IllegalArgumentException exception) {
            throw new ParameterException(spec.commandLine(), exception.getMessage(), exception);
        }
        int requestedRecords = recordCount == null ? scenario.defaultRecordCount() : recordCount;
        if (requestedRecords < 1) {
            throw new ParameterException(spec.commandLine(), "--records must be at least 1");
        }
        GenerationRequest request =
                new GenerationRequest(scenario, requestedRecords, seed, startDate, outputDirectory);
        GeneratedScenario generated = new DatasetGenerator().generate(request);
        System.out.printf(
                "Generated %s (%d canonical payments)%n", scenario.slug(), requestedRecords);
        System.out.printf("Scenario directory: %s%n", generated.scenarioDirectory());
        System.out.printf("Manifest: %s%n", generated.manifest());
        System.out.printf("ZIP archive: %s%n", generated.zipArchive());
        return CommandLine.ExitCode.OK;
    }

    @CommandLine.Spec private CommandLine.Model.CommandSpec spec;

    public static int execute(String... args) {
        return new CommandLine(new DatasetGeneratorApplication()).execute(args);
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }
}
