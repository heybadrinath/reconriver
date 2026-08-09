package io.github.heybadrinath.reconriver.shared.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("reconriver")
public record ReconRiverProperties(
        Security security, Imports imports, Batch batch, Reconciliation reconciliation) {

    public record Security(
            List<String> allowedOrigins,
            String tokenHashSecret,
            long sessionTtlHours,
            int requestsPerMinute,
            int sessionCreationsPerMinute,
            int uploadsPerHour,
            int scenarioLoadsPerHour) {
        public Duration sessionTtl() {
            return Duration.ofHours(sessionTtlHours);
        }
    }

    public record Imports(long maxUploadBytes, int maxRows, int maxColumns, int maxRowCharacters) {}

    public record Batch(int chunkSize) {}

    public record Reconciliation(String feePercent, String feeFixed, int settlementWindowDays) {}
}
