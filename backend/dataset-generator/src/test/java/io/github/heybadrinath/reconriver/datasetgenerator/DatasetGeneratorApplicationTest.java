package io.github.heybadrinath.reconriver.datasetgenerator;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatasetGeneratorApplicationTest {
    @TempDir Path temporaryDirectory;

    @Test
    void cliGeneratesARequestedScenario() {
        int exitCode =
                DatasetGeneratorApplication.execute(
                        "--scenario", "clean-settlement",
                        "--records", "12",
                        "--seed", "9",
                        "--start-date", "2026-02-01",
                        "--output", temporaryDirectory.toString());

        assertThat(exitCode).isZero();
        assertThat(temporaryDirectory.resolve("clean-settlement/scenario_manifest.json"))
                .isRegularFile();
        assertThat(temporaryDirectory.resolve("clean-settlement/clean-settlement.zip"))
                .isRegularFile();
    }

    @Test
    void cliRejectsUnknownScenariosAndInvalidCounts() {
        assertThat(
                        DatasetGeneratorApplication.execute(
                                "--scenario", "unknown", "--output", temporaryDirectory.toString()))
                .isEqualTo(2);
        assertThat(
                        DatasetGeneratorApplication.execute(
                                "--scenario", "clean-settlement",
                                "--records", "0",
                                "--output", temporaryDirectory.toString()))
                .isEqualTo(2);
    }
}
