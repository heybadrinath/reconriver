package io.github.heybadrinath.reconriver.datasetgenerator;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Objects;

public record GenerationRequest(
        ScenarioType scenario,
        int recordCount,
        long seed,
        LocalDate startDate,
        Path outputDirectory) {

    public GenerationRequest {
        Objects.requireNonNull(scenario, "scenario is required");
        Objects.requireNonNull(startDate, "start date is required");
        Objects.requireNonNull(outputDirectory, "output directory is required");
        if (recordCount < 1) {
            throw new IllegalArgumentException("record count must be at least 1");
        }
    }
}
