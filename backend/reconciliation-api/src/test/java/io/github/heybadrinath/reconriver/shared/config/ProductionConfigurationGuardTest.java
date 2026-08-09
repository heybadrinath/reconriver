package io.github.heybadrinath.reconriver.shared.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;

class ProductionConfigurationGuardTest {

    @Test
    void acceptsDirectNonLocalDatabaseAndStrongSecret() {
        assertThatCode(
                        () ->
                                guard(
                                                "jdbc:postgresql://ep-example.eu-central-1.aws.neon.tech/neondb")
                                        .validateProductionSettings())
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsPooledDatabaseEndpointForBatchAndFlyway() {
        assertThatThrownBy(
                        () ->
                                guard(
                                                "jdbc:postgresql://ep-example-pooler.eu-central-1.aws.neon.tech/neondb")
                                        .validateProductionSettings())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("direct PostgreSQL endpoint");
    }

    @Test
    void rejectsLocalCredentialsAndWeakSecret() {
        DataSourceProperties dataSource = dataSource("jdbc:postgresql://localhost:5432/reconriver");
        ReconRiverProperties properties = properties("short");
        assertThatThrownBy(
                        () ->
                                new ProductionConfigurationGuard(dataSource, properties)
                                        .validateProductionSettings())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsDevelopmentCorsOriginInProduction() {
        ReconRiverProperties invalid =
                new ReconRiverProperties(
                        new ReconRiverProperties.Security(
                                List.of("http://localhost:3000"),
                                "production-token-secret-with-at-least-thirty-two-characters",
                                24,
                                120,
                                10,
                                12,
                                4),
                        null,
                        null,
                        null);
        assertThatThrownBy(
                        () ->
                                new ProductionConfigurationGuard(
                                                dataSource(
                                                        "jdbc:postgresql://ep-example.eu-central-1.aws.neon.tech/neondb"),
                                                invalid)
                                        .validateProductionSettings())
                .hasMessageContaining("ALLOWED_ORIGINS");
    }

    private ProductionConfigurationGuard guard(String url) {
        return new ProductionConfigurationGuard(
                dataSource(url),
                properties("production-token-secret-with-at-least-thirty-two-characters"));
    }

    private DataSourceProperties dataSource(String url) {
        DataSourceProperties dataSource = new DataSourceProperties();
        dataSource.setUrl(url);
        dataSource.setUsername("neondb_owner");
        dataSource.setPassword("non-default-database-password");
        return dataSource;
    }

    private ReconRiverProperties properties(String secret) {
        return new ReconRiverProperties(
                new ReconRiverProperties.Security(
                        List.of("https://reconriver.vercel.app"), secret, 24, 120, 10, 12, 4),
                null,
                null,
                null);
    }
}
