package io.github.heybadrinath.reconriver.datasetgenerator;

import java.nio.file.Path;

public record GeneratedScenario(Path scenarioDirectory, Path manifest, Path zipArchive) {}
