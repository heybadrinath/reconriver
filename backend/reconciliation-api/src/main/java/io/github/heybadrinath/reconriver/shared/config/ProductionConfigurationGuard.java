package io.github.heybadrinath.reconriver.shared.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("production")
class ProductionConfigurationGuard {

    private static final String LOCAL_DATABASE_PASSWORD = "reconriver_local_only";
    private static final String LOCAL_TOKEN_SECRET = "local-development-secret-change-me-now";
    private static final String PRODUCTION_ORIGIN = "https://reconriver.vercel.app";

    private final DataSourceProperties dataSource;
    private final ReconRiverProperties properties;

    ProductionConfigurationGuard(DataSourceProperties dataSource, ReconRiverProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @PostConstruct
    void validateProductionSettings() {
        requireNonBlank(dataSource.getUrl(), "DB_URL");
        requireNonBlank(dataSource.getUsername(), "DB_USERNAME");
        requireNonBlank(dataSource.getPassword(), "DB_PASSWORD");

        if (dataSource.getUrl().contains("localhost")
                || dataSource.getUrl().contains("127.0.0.1")) {
            throw new IllegalStateException("DB_URL cannot target a local database in production");
        }
        if (dataSource.getUrl().contains("-pooler")) {
            throw new IllegalStateException(
                    "DB_URL must use a direct PostgreSQL endpoint for Flyway and batch processing");
        }
        if (LOCAL_DATABASE_PASSWORD.equals(dataSource.getPassword())) {
            throw new IllegalStateException(
                    "DB_PASSWORD must not use the local development credential in production");
        }

        String secret = properties.security().tokenHashSecret();
        requireNonBlank(secret, "DEMO_TOKEN_HASH_SECRET");
        if (LOCAL_TOKEN_SECRET.equals(secret) || secret.length() < 32) {
            throw new IllegalStateException(
                    "DEMO_TOKEN_HASH_SECRET must be a non-default secret of at least 32 characters");
        }
        if (!java.util.List.of(PRODUCTION_ORIGIN).equals(properties.security().allowedOrigins())) {
            throw new IllegalStateException(
                    "ALLOWED_ORIGINS must be exactly " + PRODUCTION_ORIGIN + " in production");
        }
    }

    private static void requireNonBlank(String value, String setting) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(setting + " is required in production");
        }
    }
}
