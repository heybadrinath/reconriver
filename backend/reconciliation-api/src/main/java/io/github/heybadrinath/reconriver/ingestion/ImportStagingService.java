package io.github.heybadrinath.reconriver.ingestion;

import static io.github.heybadrinath.reconriver.shared.config.SqlTime.timestamp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.heybadrinath.reconriver.shared.api.ApiException;
import io.github.heybadrinath.reconriver.shared.config.ReconRiverProperties;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class ImportStagingService {
    private static final int INSERT_BATCH_SIZE = 500;
    private static final Set<String> INTERNAL_HEADERS =
            Set.of(
                    "internal_payment_id",
                    "merchant_order_id",
                    "occurred_at",
                    "gross_amount",
                    "currency",
                    "payment_status",
                    "payment_method",
                    "synthetic_customer_reference");
    private static final Set<String> PROCESSOR_HEADERS =
            Set.of(
                    "processor_transaction_id",
                    "merchant_order_id",
                    "processor_event_type",
                    "processor_event_time",
                    "gross_amount",
                    "fee_amount",
                    "net_amount",
                    "currency",
                    "settlement_batch_id",
                    "processor_status");
    private static final Set<String> BANK_HEADERS =
            Set.of(
                    "bank_entry_id",
                    "settlement_batch_id",
                    "booked_at",
                    "credited_amount",
                    "currency",
                    "bank_reference",
                    "description");
    private static final String INSERT_STAGING_ROW =
            """
            INSERT INTO import_staging_row
                (id,import_file_id,workspace_id,source_type,row_number,raw_row,created_at)
            VALUES
                (:id,:import,:workspace,:source,:row,CAST(:raw AS jsonb),:now)
            """;

    private final JdbcClient jdbc;
    private final NamedParameterJdbcTemplate batchJdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ReconRiverProperties properties;

    ImportStagingService(
            JdbcClient jdbc,
            NamedParameterJdbcTemplate batchJdbc,
            ObjectMapper objectMapper,
            Clock clock,
            ReconRiverProperties properties) {
        this.jdbc = jdbc;
        this.batchJdbc = batchJdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.properties = properties;
    }

    @Transactional
    public StageResult stage(
            UUID workspaceId,
            SourceType sourceType,
            String fileName,
            ImportService.InputProvider inputProvider) {
        String checksum = checksum(inputProvider);
        CanonicalImport duplicate =
                jdbc.sql(
                                """
                        SELECT id,status FROM import_file
                        WHERE workspace_id=:workspace AND source_type=:source AND checksum=:checksum
                          AND status<>'DUPLICATE'
                        """)
                        .param("workspace", workspaceId)
                        .param("source", sourceType.name())
                        .param("checksum", checksum)
                        .query(
                                (rs, row) ->
                                        new CanonicalImport(
                                                rs.getObject("id", UUID.class),
                                                rs.getString("status")))
                        .optional()
                        .orElse(null);
        if (duplicate != null) {
            return existingCanonical(workspaceId, sourceType, fileName, checksum, duplicate);
        }

        validateHeaders(sourceType, inputProvider);
        UUID importId = UUID.randomUUID();
        Instant now = clock.instant();
        int inserted =
                jdbc.sql(
                                """
                INSERT INTO import_file
                    (id,workspace_id,source_type,sanitized_file_name,checksum,status,created_at,updated_at)
                VALUES
                    (:id,:workspace,:source,:name,:checksum,'VALIDATING',:now,:now)
                ON CONFLICT (workspace_id,source_type,checksum) WHERE status<>'DUPLICATE' DO NOTHING
                """)
                        .param("id", importId)
                        .param("workspace", workspaceId)
                        .param("source", sourceType.name())
                        .param("name", fileName)
                        .param("checksum", checksum)
                        .param("now", timestamp(now))
                        .update();
        if (inserted == 0) {
            CanonicalImport canonical =
                    jdbc.sql(
                                    """
                            SELECT id,status FROM import_file
                            WHERE workspace_id=:workspace AND source_type=:source AND checksum=:checksum
                              AND status<>'DUPLICATE'
                            """)
                            .param("workspace", workspaceId)
                            .param("source", sourceType.name())
                            .param("checksum", checksum)
                            .query(
                                    (rs, row) ->
                                            new CanonicalImport(
                                                    rs.getObject("id", UUID.class),
                                                    rs.getString("status")))
                            .single();
            return existingCanonical(workspaceId, sourceType, fileName, checksum, canonical);
        }

        int rows = stageRows(importId, workspaceId, sourceType, inputProvider);
        jdbc.sql(
                        """
                UPDATE import_file
                SET status='QUEUED',total_rows=:rows,updated_at=:now,version=version+1
                WHERE id=:id
                """)
                .param("rows", rows)
                .param("now", timestamp(clock.instant()))
                .param("id", importId)
                .update();
        jdbc.sql(
                        """
                INSERT INTO audit_event
                    (id,workspace_id,aggregate_type,aggregate_id,event_type,actor_type,details,occurred_at)
                VALUES
                    (:id,:workspace,'IMPORT',:aggregate,'IMPORT_QUEUED','DEMO_OPERATOR',CAST(:details AS jsonb),:now)
                """)
                .param("id", UUID.randomUUID())
                .param("workspace", workspaceId)
                .param("aggregate", importId)
                .param(
                        "details",
                        json(
                                Map.of(
                                        "sourceType",
                                        sourceType.name(),
                                        "fileName",
                                        fileName,
                                        "checksum",
                                        checksum,
                                        "rows",
                                        rows)))
                .param("now", timestamp(clock.instant()))
                .update();
        return new StageResult(importId, true);
    }

    private StageResult existingCanonical(
            UUID workspaceId,
            SourceType sourceType,
            String fileName,
            String checksum,
            CanonicalImport canonical) {
        if (canonical.status().equals("COMPLETED")
                || canonical.status().equals("COMPLETED_WITH_ERRORS")) {
            return duplicateAttempt(workspaceId, sourceType, fileName, checksum, canonical.id());
        }
        if (canonical.status().equals("FAILED")) {
            int claimed =
                    jdbc.sql(
                                    """
                    UPDATE import_file
                    SET status='QUEUED',failure_message=NULL,updated_at=:now,version=version+1
                    WHERE id=:id AND status='FAILED'
                    """)
                            .param("now", timestamp(clock.instant()))
                            .param("id", canonical.id())
                            .update();
            return new StageResult(canonical.id(), claimed == 1);
        }
        return new StageResult(canonical.id(), false);
    }

    private StageResult duplicateAttempt(
            UUID workspaceId,
            SourceType sourceType,
            String fileName,
            String checksum,
            UUID canonicalId) {
        UUID duplicateId = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.sql(
                        """
                INSERT INTO import_file
                    (id,workspace_id,source_type,sanitized_file_name,checksum,status,duplicate_of_id,created_at,updated_at)
                VALUES
                    (:id,:workspace,:source,:name,:checksum,'DUPLICATE',:canonical,:now,:now)
                """)
                .param("id", duplicateId)
                .param("workspace", workspaceId)
                .param("source", sourceType.name())
                .param("name", fileName)
                .param("checksum", checksum)
                .param("canonical", canonicalId)
                .param("now", timestamp(now))
                .update();
        jdbc.sql(
                        """
                INSERT INTO audit_event
                    (id,workspace_id,aggregate_type,aggregate_id,event_type,actor_type,details,occurred_at)
                VALUES
                    (:id,:workspace,'IMPORT',:aggregate,'IMPORT_DUPLICATE_SKIPPED','DEMO_OPERATOR',
                     CAST(:details AS jsonb),:now)
                """)
                .param("id", UUID.randomUUID())
                .param("workspace", workspaceId)
                .param("aggregate", duplicateId)
                .param(
                        "details",
                        json(
                                Map.of(
                                        "sourceType",
                                        sourceType.name(),
                                        "fileName",
                                        fileName,
                                        "checksum",
                                        checksum,
                                        "duplicateOfId",
                                        canonicalId.toString())))
                .param("now", timestamp(now))
                .update();
        return new StageResult(duplicateId, false);
    }

    private String checksum(ImportService.InputProvider provider) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input =
                    new DigestInputStream(new BufferedInputStream(provider.open()), digest)) {
                byte[] buffer = new byte[8192];
                long total = 0;
                int read;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > properties.imports().maxUploadBytes()) {
                        throw new ApiException(
                                HttpStatus.PAYLOAD_TOO_LARGE,
                                "UPLOAD_TOO_LARGE",
                                "The CSV exceeds the 10 MB public upload limit.");
                    }
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException | NoSuchAlgorithmException exception) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "UPLOAD_UNREADABLE", "The CSV could not be read.");
        }
    }

    private void validateHeaders(SourceType sourceType, ImportService.InputProvider provider) {
        try (CSVParser parser = parser(provider.open())) {
            List<String> headers = new ArrayList<>(parser.getHeaderNames());
            if (!headers.isEmpty()) {
                headers.set(0, headers.getFirst().replace("\uFEFF", ""));
            }
            Set<String> actual = new HashSet<>(headers);
            Set<String> required = requiredHeaders(sourceType);
            if (headers.size() != actual.size()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "DUPLICATE_HEADERS",
                        "The CSV contains duplicate column names.");
            }
            if (!actual.equals(required)) {
                Set<String> missing = new HashSet<>(required);
                missing.removeAll(actual);
                Set<String> unexpected = new HashSet<>(actual);
                unexpected.removeAll(required);
                String detail =
                        !missing.isEmpty()
                                ? "This file is missing the `"
                                        + missing.iterator().next()
                                        + "` column."
                                : "The CSV contains unsupported columns: "
                                        + String.join(", ", unexpected)
                                        + '.';
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CSV_HEADERS", detail);
            }
            if (headers.size() > properties.imports().maxColumns()) {
                throw new ApiException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "TOO_MANY_COLUMNS",
                        "The CSV exceeds the public column limit.");
            }
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_CSV",
                    "The CSV header could not be parsed.");
        }
    }

    private int stageRows(
            UUID importId,
            UUID workspaceId,
            SourceType sourceType,
            ImportService.InputProvider provider) {
        try (CSVParser parser = parser(provider.open())) {
            List<SqlParameterSource> pending = new ArrayList<>(INSERT_BATCH_SIZE);
            int rows = 0;
            Instant stagedAt = clock.instant();
            for (CSVRecord record : parser) {
                rows++;
                if (rows > properties.imports().maxRows()) {
                    throw new ApiException(
                            HttpStatus.PAYLOAD_TOO_LARGE,
                            "ROW_LIMIT_EXCEEDED",
                            "Workspace imports are limited to 25,000 data rows.");
                }
                if (record.size() > properties.imports().maxColumns()) {
                    throw new ApiException(
                            HttpStatus.UNPROCESSABLE_ENTITY,
                            "TOO_MANY_COLUMNS",
                            "A CSV row exceeds the public column limit.");
                }
                LinkedHashMap<String, String> values = new LinkedHashMap<>();
                int characters = 0;
                for (String header : parser.getHeaderNames()) {
                    String normalizedHeader = header.replace("\uFEFF", "");
                    String value = record.isMapped(header) ? record.get(header) : "";
                    characters += value.length();
                    values.put(normalizedHeader, value);
                }
                if (characters > properties.imports().maxRowCharacters()) {
                    values = new LinkedHashMap<>();
                    values.put("_row_error", "ROW_TOO_LONG");
                }
                pending.add(
                        new MapSqlParameterSource()
                                .addValue("id", UUID.randomUUID())
                                .addValue("import", importId)
                                .addValue("workspace", workspaceId)
                                .addValue("source", sourceType.name())
                                .addValue("row", rows + 1)
                                .addValue("raw", json(values))
                                .addValue("now", timestamp(stagedAt)));
                if (pending.size() == INSERT_BATCH_SIZE) {
                    insertBatch(pending);
                }
            }
            insertBatch(pending);
            return rows;
        } catch (IOException | IllegalArgumentException exception) {
            if (exception instanceof ApiException apiException) {
                throw apiException;
            }
            throw new ApiException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "INVALID_CSV",
                    "The CSV body could not be parsed.");
        }
    }

    private void insertBatch(List<SqlParameterSource> pending) {
        if (pending.isEmpty()) {
            return;
        }
        batchJdbc.batchUpdate(INSERT_STAGING_ROW, pending.toArray(SqlParameterSource[]::new));
        pending.clear();
    }

    private CSVParser parser(InputStream input) throws IOException {
        return CSVFormat.RFC4180
                .builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setIgnoreEmptyLines(true)
                .get()
                .parse(new InputStreamReader(input, StandardCharsets.UTF_8));
    }

    private Set<String> requiredHeaders(SourceType sourceType) {
        return switch (sourceType) {
            case INTERNAL -> INTERNAL_HEADERS;
            case PROCESSOR -> PROCESSOR_HEADERS;
            case BANK -> BANK_HEADERS;
        };
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Unable to serialize staged data", exception);
        }
    }

    record StageResult(UUID importId, boolean queued) {}

    private record CanonicalImport(UUID id, String status) {}
}
