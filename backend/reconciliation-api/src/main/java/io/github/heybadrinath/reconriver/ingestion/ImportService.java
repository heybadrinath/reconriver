package io.github.heybadrinath.reconriver.ingestion;

import static io.github.heybadrinath.reconriver.shared.config.SqlTime.timestamp;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.heybadrinath.reconriver.shared.api.ApiException;
import io.github.heybadrinath.reconriver.shared.api.PageResponse;
import io.github.heybadrinath.reconriver.shared.config.ReconRiverProperties;
import io.github.heybadrinath.reconriver.shared.events.LifecycleEvent;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ImportService {
    private static final Set<String> CSV_CONTENT_TYPES =
            Set.of(
                    "text/csv",
                    "application/csv",
                    "application/vnd.ms-excel",
                    "text/plain",
                    "application/octet-stream");
    private static final TypeReference<Map<String, String>> STRING_MAP = new TypeReference<>() {};

    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final ReconRiverProperties properties;
    private final JobOperator jobOperator;
    private final Job importJob;
    private final ApplicationEventPublisher events;
    private final ImportStagingService staging;

    ImportService(
            JdbcClient jdbc,
            ObjectMapper objectMapper,
            Clock clock,
            ReconRiverProperties properties,
            JobOperator jobOperator,
            @Lazy @Qualifier("csvImportJob") Job importJob,
            ApplicationEventPublisher events,
            ImportStagingService staging) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.properties = properties;
        this.jobOperator = jobOperator;
        this.importJob = importJob;
        this.events = events;
        this.staging = staging;
    }

    public ImportView upload(UUID workspaceId, SourceType sourceType, MultipartFile file) {
        validateUpload(file);
        String fileName = sanitizeFileName(file.getOriginalFilename());
        ImportStagingService.StageResult staged =
                staging.stage(workspaceId, sourceType, fileName, () -> file.getInputStream());
        if (staged.queued()) {
            launch(staged.importId(), workspaceId);
        }
        return get(workspaceId, staged.importId());
    }

    public ImportView stageGenerated(
            UUID workspaceId, SourceType sourceType, String fileName, byte[] content) {
        ImportStagingService.StageResult staged =
                staging.stage(
                        workspaceId,
                        sourceType,
                        fileName,
                        () -> new java.io.ByteArrayInputStream(content));
        if (staged.queued()) {
            launch(staged.importId(), workspaceId);
        }
        return get(workspaceId, staged.importId());
    }

    public void launch(UUID importId, UUID workspaceId) {
        try {
            JobExecution execution =
                    jobOperator.start(
                            importJob,
                            new JobParametersBuilder()
                                    .addString("importId", importId.toString())
                                    .addString("workspaceId", workspaceId.toString())
                                    .toJobParameters());
            jdbc.sql(
                            "UPDATE import_file SET job_execution_id=:execution,updated_at=:now WHERE id=:id AND workspace_id=:workspace")
                    .param("execution", execution.getId())
                    .param("now", timestamp(clock.instant()))
                    .param("id", importId)
                    .param("workspace", workspaceId)
                    .update();
        } catch (Exception exception) {
            markLaunchFailure(importId, workspaceId, exception);
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IMPORT_NOT_STARTED",
                    "The import could not be started because it is already running or completed.");
        }
    }

    public ImportView get(UUID workspaceId, UUID importId) {
        return jdbc.sql("SELECT * FROM import_file WHERE id=:id AND workspace_id=:workspace")
                .param("id", importId)
                .param("workspace", workspaceId)
                .query(this::importView)
                .optional()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "IMPORT_NOT_FOUND",
                                        "No import exists with that ID in this workspace."));
    }

    public PageResponse<ImportView> list(
            UUID workspaceId, int page, int size, String sourceType, String status) {
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        StringBuilder where = new StringBuilder("workspace_id=:workspace");
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("workspace", workspaceId);
        if (sourceType != null && !sourceType.isBlank()) {
            SourceType parsed;
            try {
                parsed = SourceType.valueOf(sourceType.toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_SOURCE_TYPE",
                        "sourceType must be INTERNAL, PROCESSOR or BANK.");
            }
            where.append(" AND source_type=:source");
            parameters.put("source", parsed.name());
        }
        if (status != null && !status.isBlank()) {
            where.append(" AND status=:status");
            parameters.put("status", status.toUpperCase());
        }
        long total =
                queryWithParams("SELECT COUNT(*) FROM import_file WHERE " + where, parameters)
                        .query(Long.class)
                        .single();
        parameters.put("limit", safeSize);
        parameters.put("offset", safePage * safeSize);
        List<ImportView> items =
                queryWithParams(
                                "SELECT * FROM import_file WHERE "
                                        + where
                                        + " ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset",
                                parameters)
                        .query(this::importView)
                        .list();
        return PageResponse.of(items, safePage, safeSize, total);
    }

    public PageResponse<ImportRowErrorView> errors(
            UUID workspaceId, UUID importId, int page, int size) {
        get(workspaceId, importId);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int safePage = Math.max(page, 0);
        long total =
                jdbc.sql(
                                "SELECT COUNT(*) FROM import_row_error WHERE workspace_id=:workspace AND import_file_id=:import")
                        .param("workspace", workspaceId)
                        .param("import", importId)
                        .query(Long.class)
                        .single();
        List<ImportRowErrorView> items =
                jdbc.sql(
                                """
                        SELECT id,row_number,error_code,message,created_at FROM import_row_error
                        WHERE workspace_id=:workspace AND import_file_id=:import
                        ORDER BY row_number,id LIMIT :limit OFFSET :offset
                        """)
                        .param("workspace", workspaceId)
                        .param("import", importId)
                        .param("limit", safeSize)
                        .param("offset", safePage * safeSize)
                        .query(
                                (rs, row) ->
                                        new ImportRowErrorView(
                                                rs.getObject("id", UUID.class),
                                                rs.getInt("row_number"),
                                                rs.getString("error_code"),
                                                rs.getString("message"),
                                                rs.getTimestamp("created_at").toInstant()))
                        .list();
        return PageResponse.of(items, safePage, safeSize, total);
    }

    List<StagingRow> nextRows(UUID importId, int afterRowNumber, int limit) {
        return jdbc.sql(
                        """
                        SELECT id,import_file_id,workspace_id,source_type,row_number,raw_row::text AS raw_json
                        FROM import_staging_row WHERE import_file_id=:import AND processed=false AND row_number>:afterRow
                        ORDER BY row_number LIMIT :limit
                        """)
                .param("import", importId)
                .param("afterRow", afterRowNumber)
                .param("limit", limit)
                .query(
                        (rs, row) ->
                                new StagingRow(
                                        rs.getObject("id", UUID.class),
                                                rs.getObject("import_file_id", UUID.class),
                                        rs.getObject("workspace_id", UUID.class),
                                                SourceType.valueOf(rs.getString("source_type")),
                                        rs.getInt("row_number"), rs.getString("raw_json")))
                .list();
    }

    @Transactional
    public void processRows(List<? extends StagingRow> rows) {
        for (StagingRow row : rows) {
            try {
                Map<String, String> values = objectMapper.readValue(row.rawJson(), STRING_MAP);
                switch (row.sourceType()) {
                    case INTERNAL -> insertInternal(row, values);
                    case PROCESSOR -> insertProcessor(row, values);
                    case BANK -> insertBank(row, values);
                }
                jdbc.sql(
                                "UPDATE import_file SET imported_rows=imported_rows+1,updated_at=:now WHERE id=:id")
                        .param("now", timestamp(clock.instant()))
                        .param("id", row.importFileId())
                        .update();
            } catch (DataAccessException exception) {
                throw exception;
            } catch (Exception exception) {
                quarantine(row, classify(exception), safeValidationMessage(exception));
            }
            jdbc.sql("UPDATE import_staging_row SET processed=true WHERE id=:id")
                    .param("id", row.id())
                    .update();
        }
    }

    @Transactional
    public void beforeJob(UUID importId, UUID workspaceId) {
        Instant now = clock.instant();
        jdbc.sql(
                        "UPDATE import_file SET status='RUNNING',failure_message=NULL,updated_at=:now,version=version+1 WHERE id=:id AND workspace_id=:workspace")
                .param("now", timestamp(now))
                .param("id", importId)
                .param("workspace", workspaceId)
                .update();
        audit(workspaceId, importId, "IMPORT_STARTED", Map.of());
    }

    @Transactional
    public void afterJob(UUID importId, UUID workspaceId, boolean success, String failure) {
        Instant now = clock.instant();
        if (success) {
            jdbc.sql(
                            """
                    UPDATE import_file SET status=CASE WHEN quarantined_rows>0 THEN 'COMPLETED_WITH_ERRORS' ELSE 'COMPLETED' END,
                    failure_message=NULL,updated_at=:now,version=version+1 WHERE id=:id AND workspace_id=:workspace
                    """)
                    .param("now", timestamp(now))
                    .param("id", importId)
                    .param("workspace", workspaceId)
                    .update();
            audit(workspaceId, importId, "IMPORT_COMPLETED", Map.of());
            events.publishEvent(
                    new LifecycleEvent("ImportCompleted", workspaceId, importId, now, Map.of()));
        } else {
            jdbc.sql(
                            "UPDATE import_file SET status='FAILED',failure_message=:failure,updated_at=:now,version=version+1 WHERE id=:id AND workspace_id=:workspace")
                    .param("failure", truncate(failure, 1000))
                    .param("now", timestamp(now))
                    .param("id", importId)
                    .param("workspace", workspaceId)
                    .update();
            audit(workspaceId, importId, "IMPORT_FAILED", Map.of("reason", truncate(failure, 300)));
            events.publishEvent(
                    new LifecycleEvent(
                            "ImportFailed",
                            workspaceId,
                            importId,
                            now,
                            Map.of("reason", truncate(failure, 300))));
        }
    }

    private void validateUpload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "EMPTY_UPLOAD", "Choose a non-empty CSV file.");
        }
        if (file.getSize() > properties.imports().maxUploadBytes()) {
            throw new ApiException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "UPLOAD_TOO_LARGE",
                    "The CSV exceeds the 10 MB public upload limit.");
        }
        String contentType = file.getContentType();
        if (contentType != null && !CSV_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            throw new ApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "CSV_REQUIRED",
                    "Only CSV uploads are accepted.");
        }
    }

    private String sanitizeFileName(String original) {
        if (original == null || original.isBlank()) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST, "INVALID_FILE_NAME", "The CSV must have a file name.");
        }
        if (original.contains("/")
                || original.contains("\\")
                || original.contains("..")
                || original.indexOf('\0') >= 0) {
            throw new ApiException(
                    HttpStatus.BAD_REQUEST,
                    "UNSAFE_FILE_NAME",
                    "The file name contains a path or unsafe sequence.");
        }
        String clean = original.replaceAll("[^A-Za-z0-9._-]", "_");
        if (!clean.toLowerCase().endsWith(".csv")) {
            throw new ApiException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "CSV_REQUIRED",
                    "The uploaded file name must end in .csv.");
        }
        return truncate(clean, 255);
    }

    private void insertInternal(StagingRow row, Map<String, String> values) {
        failStoredRowError(values);
        jdbc.sql(
                        """
                INSERT INTO internal_transaction (id,workspace_id,import_file_id,source_row_number,internal_payment_id,
                merchant_order_id,occurred_at,gross_amount,currency,payment_status,payment_method,synthetic_customer_reference,created_at)
                VALUES (:id,:workspace,:import,:row,:payment,:orderId,:occurred,:gross,:currency,:status,:method,:customer,:now)
                """)
                .param("id", UUID.randomUUID())
                .param("workspace", row.workspaceId())
                .param("import", row.importFileId())
                .param("row", row.rowNumber())
                .param("payment", required(values, "internal_payment_id", 128))
                .param("orderId", required(values, "merchant_order_id", 128))
                .param("occurred", timestamp(instant(values, "occurred_at")))
                .param("gross", money(values, "gross_amount"))
                .param("currency", currency(values))
                .param("status", required(values, "payment_status", 32))
                .param("method", required(values, "payment_method", 32))
                .param("customer", required(values, "synthetic_customer_reference", 128))
                .param("now", timestamp(clock.instant()))
                .update();
    }

    private void insertProcessor(StagingRow row, Map<String, String> values) {
        failStoredRowError(values);
        BigDecimal gross = money(values, "gross_amount");
        BigDecimal fee = money(values, "fee_amount");
        BigDecimal net = money(values, "net_amount");
        if (gross.subtract(fee).compareTo(net) != 0) {
            throw new IllegalArgumentException(
                    "net_amount must equal gross_amount minus fee_amount");
        }
        jdbc.sql(
                        """
                INSERT INTO processor_transaction (id,workspace_id,import_file_id,source_row_number,processor_transaction_id,
                merchant_order_id,processor_event_type,processor_event_time,gross_amount,fee_amount,net_amount,currency,
                settlement_batch_id,processor_status,created_at)
                VALUES (:id,:workspace,:import,:row,:transaction,:orderId,:eventType,:eventTime,:gross,:fee,:net,:currency,:batch,:status,:now)
                """)
                .param("id", UUID.randomUUID())
                .param("workspace", row.workspaceId())
                .param("import", row.importFileId())
                .param("row", row.rowNumber())
                .param("transaction", required(values, "processor_transaction_id", 128))
                .param("orderId", required(values, "merchant_order_id", 128))
                .param("eventType", required(values, "processor_event_type", 32))
                .param("eventTime", timestamp(instant(values, "processor_event_time")))
                .param("gross", gross)
                .param("fee", fee)
                .param("net", net)
                .param("currency", currency(values))
                .param("batch", optional(values, "settlement_batch_id", 128))
                .param("status", required(values, "processor_status", 32))
                .param("now", timestamp(clock.instant()))
                .update();
    }

    private void insertBank(StagingRow row, Map<String, String> values) {
        failStoredRowError(values);
        jdbc.sql(
                        """
                INSERT INTO bank_settlement (id,workspace_id,import_file_id,source_row_number,bank_entry_id,settlement_batch_id,
                booked_at,credited_amount,currency,bank_reference,description,created_at)
                VALUES (:id,:workspace,:import,:row,:entry,:batch,:booked,:credited,:currency,:reference,:description,:now)
                """)
                .param("id", UUID.randomUUID())
                .param("workspace", row.workspaceId())
                .param("import", row.importFileId())
                .param("row", row.rowNumber())
                .param("entry", required(values, "bank_entry_id", 128))
                .param("batch", required(values, "settlement_batch_id", 128))
                .param("booked", timestamp(bankInstant(values, "booked_at")))
                .param("credited", money(values, "credited_amount"))
                .param("currency", currency(values))
                .param("reference", required(values, "bank_reference", 128))
                .param("description", required(values, "description", 500))
                .param("now", timestamp(clock.instant()))
                .update();
    }

    private void quarantine(StagingRow row, String code, String message) {
        jdbc.sql(
                        """
                INSERT INTO import_row_error (id,import_file_id,workspace_id,row_number,error_code,message,raw_row,created_at)
                VALUES (:id,:import,:workspace,:row,:code,:message,CAST(:raw AS jsonb),:now)
                ON CONFLICT (import_file_id,row_number,error_code) DO NOTHING
                """)
                .param("id", UUID.randomUUID())
                .param("import", row.importFileId())
                .param("workspace", row.workspaceId())
                .param("row", row.rowNumber())
                .param("code", code)
                .param("message", truncate(message, 500))
                .param("raw", row.rawJson())
                .param("now", timestamp(clock.instant()))
                .update();
        jdbc.sql(
                        "UPDATE import_file SET quarantined_rows=quarantined_rows+1,updated_at=:now WHERE id=:id")
                .param("now", timestamp(clock.instant()))
                .param("id", row.importFileId())
                .update();
    }

    private ImportView importView(ResultSet rs, int row) throws SQLException {
        return new ImportView(
                rs.getObject("id", UUID.class),
                SourceType.valueOf(rs.getString("source_type")),
                rs.getString("sanitized_file_name"),
                rs.getString("checksum"),
                rs.getString("status"),
                rs.getInt("total_rows"),
                rs.getInt("imported_rows"),
                rs.getInt("quarantined_rows"),
                rs.getObject("duplicate_of_id", UUID.class),
                rs.getString("failure_message"),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private JdbcClient.StatementSpec queryWithParams(String sql, Map<String, Object> parameters) {
        JdbcClient.StatementSpec statement = jdbc.sql(sql);
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            statement = statement.param(parameter.getKey(), parameter.getValue());
        }
        return statement;
    }

    private String required(Map<String, String> values, String name, int maxLength) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return value.trim();
    }

    private String optional(Map<String, String> values, String name, int maxLength) {
        String value = values.get(name);
        if (value == null || value.isBlank()) {
            return null;
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " exceeds " + maxLength + " characters");
        }
        return value.trim();
    }

    private BigDecimal money(Map<String, String> values, String name) {
        try {
            return new BigDecimal(required(values, name, 40)).setScale(4, RoundingMode.UNNECESSARY);
        } catch (ArithmeticException | NumberFormatException exception) {
            throw new IllegalArgumentException(
                    name + " must be a decimal with at most four fractional digits");
        }
    }

    private Instant instant(Map<String, String> values, String name) {
        String text = required(values, name, 80);
        try {
            return Instant.parse(text);
        } catch (DateTimeParseException ignored) {
            try {
                return OffsetDateTime.parse(text).toInstant();
            } catch (DateTimeParseException exception) {
                throw new IllegalArgumentException(name + " must be an ISO-8601 timestamp");
            }
        }
    }

    private Instant bankInstant(Map<String, String> values, String name) {
        try {
            return instant(values, name);
        } catch (IllegalArgumentException timestampFailure) {
            try {
                return LocalDate.parse(required(values, name, 80))
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant();
            } catch (DateTimeParseException dateFailure) {
                throw new IllegalArgumentException(name + " must be an ISO-8601 timestamp or date");
            }
        }
    }

    private String currency(Map<String, String> values) {
        String currency = required(values, "currency", 3).toUpperCase();
        if (!currency.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("currency must be a three-letter uppercase code");
        }
        return currency;
    }

    private void failStoredRowError(Map<String, String> values) {
        if (values.containsKey("_row_error")) {
            throw new IllegalArgumentException("The row exceeds the maximum character count");
        }
    }

    private String classify(Exception exception) {
        String message = exception.getMessage();
        if (message != null && message.contains("row exceeds")) {
            return "ROW_TOO_LONG";
        }
        if (message != null && message.contains("timestamp")) {
            return "INVALID_TIMESTAMP";
        }
        if (message != null && (message.contains("amount") || message.contains("decimal"))) {
            return "INVALID_AMOUNT";
        }
        return "INVALID_SOURCE_ROW";
    }

    private String safeValidationMessage(Exception exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "The source row is invalid." : message;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to serialize staged row", exception);
        }
    }

    private void audit(UUID workspaceId, UUID aggregateId, String event, Map<String, ?> details) {
        jdbc.sql(
                        "INSERT INTO audit_event (id,workspace_id,aggregate_type,aggregate_id,event_type,actor_type,details,occurred_at) VALUES (:id,:workspace,'IMPORT',:aggregate,:event,'WORKSPACE_OPERATOR',CAST(:details AS jsonb),:now)")
                .param("id", UUID.randomUUID())
                .param("workspace", workspaceId)
                .param("aggregate", aggregateId)
                .param("event", event)
                .param("details", writeJson(details))
                .param("now", timestamp(clock.instant()))
                .update();
    }

    private void markLaunchFailure(UUID importId, UUID workspaceId, Exception exception) {
        jdbc.sql(
                        "UPDATE import_file SET status='FAILED',failure_message=:message,updated_at=:now WHERE id=:id AND workspace_id=:workspace")
                .param("message", truncate(exception.getMessage(), 1000))
                .param("now", timestamp(clock.instant()))
                .param("id", importId)
                .param("workspace", workspaceId)
                .update();
    }

    private String truncate(String value, int length) {
        if (value == null) {
            return "Unknown failure";
        }
        return value.length() <= length ? value : value.substring(0, length);
    }

    @FunctionalInterface
    interface InputProvider {
        InputStream open() throws IOException;
    }
}
