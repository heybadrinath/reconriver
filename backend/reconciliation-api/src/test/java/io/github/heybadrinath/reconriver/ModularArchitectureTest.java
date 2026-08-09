package io.github.heybadrinath.reconriver;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularArchitectureTest {

    @Test
    void applicationModulesHaveNoCyclesOrIllegalDependencies() {
        ApplicationModules.of(ReconRiverApplication.class).verify();
    }
}
