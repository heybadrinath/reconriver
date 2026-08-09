package io.github.heybadrinath.reconriver.reconciliation;

import static io.github.heybadrinath.reconriver.shared.config.SqlTime.timestamp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.heybadrinath.reconriver.shared.api.ApiException;
import io.github.heybadrinath.reconriver.shared.api.PageResponse;
import io.github.heybadrinath.reconriver.shared.events.LifecycleEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationRunService {
    private final JdbcClient jdbc;
    private final JobOperator jobOperator;
    private final Job reconciliationJob;
    private final ReconciliationPolicy policy;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    ReconciliationRunService(
            JdbcClient jdbc,
            JobOperator jobOperator,
            @Lazy @Qualifier("reconciliationJob") Job reconciliationJob,
            ReconciliationPolicy policy,
            Clock clock,
            ObjectMapper objectMapper,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.jobOperator = jobOperator;
        this.reconciliationJob = reconciliationJob;
        this.policy = policy;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    public RunView create(UUID workspaceId, Integer controlledFailureAfterChunks) {
        assertImportsReady(workspaceId);
        UUID runId = UUID.randomUUID();
        Instant now = clock.instant();
        try {
            jdbc.sql(
                            """
                    INSERT INTO reconciliation_run (id,workspace_id,status,controlled_failure_after_chunks,created_at,updated_at)
                    VALUES (:id,:workspace,'QUEUED',:failureAfter,:now,:now)
                    """)
                    .param("id", runId)
                    .param("workspace", workspaceId)
                    .param("failureAfter", controlledFailureAfterChunks)
                    .param("now", timestamp(now))
                    .update();
        } catch (DataIntegrityViolationException exception) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RUN_ALREADY_ACTIVE",
                    "This workspace already has a queued or running reconciliation.");
        }
        audit(
                workspaceId,
                runId,
                "RECONCILIATION_RUN_QUEUED",
                Map.of(
                        "controlledFailureAfterChunks",
                        controlledFailureAfterChunks == null
                                ? "disabled"
                                : controlledFailureAfterChunks));
        launch(runId, workspaceId, controlledFailureAfterChunks);
        return get(workspaceId, runId);
    }

    public RunView restart(UUID workspaceId, UUID runId) {
        RestartState state =
                jdbc.sql(
                                """
                        SELECT status,controlled_failure_after_chunks
                        FROM reconciliation_run WHERE id=:id AND workspace_id=:workspace
                        """)
                        .param("id", runId)
                        .param("workspace", workspaceId)
                        .query(
                                (rs, row) ->
                                        new RestartState(
                                                rs.getString("status"),
                                                (Integer)
                                                        rs.getObject(
                                                                "controlled_failure_after_chunks")))
                        .optional()
                        .orElseThrow(
                                () ->
                                        new ApiException(
                                                HttpStatus.NOT_FOUND,
                                                "RUN_NOT_FOUND",
                                                "No reconciliation run exists with that ID in this workspace."));
        if (!state.status().equals("FAILED")) {
            if (state.status().equals("QUEUED")
                    || state.status().equals("STARTING")
                    || state.status().equals("RUNNING")) {
                throw new ApiException(
                        HttpStatus.CONFLICT,
                        "RUN_RESTART_IN_PROGRESS",
                        "This reconciliation run was already claimed for restart.");
            }
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RUN_NOT_RESTARTABLE",
                    "Only a failed reconciliation run can be restarted.");
        }
        int claimed =
                jdbc.sql(
                                "UPDATE reconciliation_run SET status='QUEUED',failure_message=NULL,completed_at=NULL,updated_at=:now,version=version+1 WHERE id=:id AND workspace_id=:workspace AND status='FAILED'")
                        .param("now", timestamp(clock.instant()))
                        .param("id", runId)
                        .param("workspace", workspaceId)
                        .update();
        if (claimed != 1) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RUN_RESTART_IN_PROGRESS",
                    "This reconciliation run was already claimed for restart.");
        }
        audit(workspaceId, runId, "RECONCILIATION_RUN_RESTARTED", Map.of());
        launch(runId, workspaceId, state.controlledFailureAfterChunks());
        return get(workspaceId, runId);
    }

    private void launch(UUID runId, UUID workspaceId, Integer controlledFailureAfterChunks) {
        try {
            JobParametersBuilder parameters =
                    new JobParametersBuilder()
                            .addString("runId", runId.toString())
                            .addString("workspaceId", workspaceId.toString());
            if (controlledFailureAfterChunks != null) {
                parameters.addLong(
                        "controlledFailureAfterChunks", controlledFailureAfterChunks.longValue());
            }
            JobExecution execution =
                    jobOperator.start(reconciliationJob, parameters.toJobParameters());
            jdbc.sql(
                            "UPDATE reconciliation_run SET job_execution_id=:execution,updated_at=:now WHERE id=:id AND workspace_id=:workspace")
                    .param("execution", execution.getId())
                    .param("now", timestamp(clock.instant()))
                    .param("id", runId)
                    .param("workspace", workspaceId)
                    .update();
        } catch (Exception exception) {
            afterJob(runId, workspaceId, false, exception.getMessage());
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "RUN_NOT_STARTED",
                    "The reconciliation could not be started or restarted.");
        }
    }

    public RunView get(UUID workspaceId, UUID runId) {
        int processed = processed(runId);
        return jdbc.sql("SELECT * FROM reconciliation_run WHERE id=:id AND workspace_id=:workspace")
                .param("id", runId)
                .param("workspace", workspaceId)
                .query((rs, row) -> runView(rs, processed))
                .optional()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "RUN_NOT_FOUND",
                                        "No reconciliation run exists with that ID in this workspace."));
    }

    public PageResponse<RunView> list(UUID workspaceId, int page, int size, String status) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        String where =
                "workspace_id=:workspace"
                        + (status == null || status.isBlank() ? "" : " AND status=:status");
        JdbcClient.StatementSpec countStatement =
                jdbc.sql("SELECT COUNT(*) FROM reconciliation_run WHERE " + where)
                        .param("workspace", workspaceId);
        JdbcClient.StatementSpec itemStatement =
                jdbc.sql(
                                "SELECT * FROM reconciliation_run WHERE "
                                        + where
                                        + " ORDER BY created_at DESC,id DESC LIMIT :limit OFFSET :offset")
                        .param("workspace", workspaceId)
                        .param("limit", safeSize)
                        .param("offset", safePage * safeSize);
        if (status != null && !status.isBlank()) {
            countStatement = countStatement.param("status", status.toUpperCase());
            itemStatement = itemStatement.param("status", status.toUpperCase());
        }
        long total = countStatement.query(Long.class).single();
        List<RunView> items =
                itemStatement
                        .query((rs, row) -> runView(rs, processed(rs.getObject("id", UUID.class))))
                        .list();
        return PageResponse.of(items, safePage, safeSize, total);
    }

    public ProgressView progress(UUID workspaceId, UUID runId) {
        RunView run = get(workspaceId, runId);
        BatchCounts batch =
                jdbc.sql(
                                """
                        SELECT COALESCE(SUM(read_count),0) AS reads,COALESCE(SUM(write_count),0) AS writes,
                        COALESCE(SUM(read_skip_count+write_skip_count+process_skip_count),0) AS skips
                        FROM batch_step_execution WHERE job_execution_id=(SELECT job_execution_id FROM reconciliation_run WHERE id=:id)
                        """)
                        .param("id", runId)
                        .query(
                                (rs, row) ->
                                        new BatchCounts(
                                                rs.getLong("reads"),
                                                rs.getLong("writes"),
                                                rs.getLong("skips")))
                        .single();
        Instant updatedAt =
                jdbc.sql("SELECT updated_at FROM reconciliation_run WHERE id=:id")
                        .param("id", runId)
                        .query(Instant.class)
                        .single();
        return new ProgressView(
                runId,
                run.status(),
                run.totalWorkItems(),
                run.processedItems(),
                run.progressPercent(),
                batch.reads(),
                batch.writes(),
                batch.skips(),
                run.startedAt(),
                updatedAt,
                run.failureMessage(),
                run.restartable());
    }

    @Transactional
    public void prepareWork(UUID runId, UUID workspaceId) {
        Instant now = clock.instant();
        jdbc.sql(
                        """
                INSERT INTO settlement_batch (id,workspace_id,external_batch_id,currency,expected_credit,first_event_at,last_event_at,transaction_count,created_at,updated_at)
                SELECT gen_random_uuid(),workspace_id,settlement_batch_id,currency,SUM(net_amount),MIN(processor_event_time),MAX(processor_event_time),COUNT(*),:now,:now
                FROM processor_transaction WHERE workspace_id=:workspace AND settlement_batch_id IS NOT NULL
                GROUP BY workspace_id,settlement_batch_id,currency
                ON CONFLICT (workspace_id,external_batch_id,currency) DO UPDATE SET expected_credit=EXCLUDED.expected_credit,
                first_event_at=EXCLUDED.first_event_at,last_event_at=EXCLUDED.last_event_at,transaction_count=EXCLUDED.transaction_count,updated_at=EXCLUDED.updated_at
                """)
                .param("workspace", workspaceId)
                .param("now", timestamp(now))
                .update();
        jdbc.sql(
                        """
                INSERT INTO reconciliation_work_item (id,run_id,workspace_id,work_type,work_key,created_at)
                SELECT gen_random_uuid(),:run,:workspace,'ORDER',merchant_order_id,:now FROM (
                  SELECT merchant_order_id FROM internal_transaction WHERE workspace_id=:workspace
                  UNION SELECT merchant_order_id FROM processor_transaction WHERE workspace_id=:workspace
                ) orders ON CONFLICT (run_id,work_type,work_key) DO NOTHING
                """)
                .param("run", runId)
                .param("workspace", workspaceId)
                .param("now", timestamp(now))
                .update();
        jdbc.sql(
                        """
                INSERT INTO reconciliation_work_item (id,run_id,workspace_id,work_type,work_key,source_error_id,created_at)
                SELECT gen_random_uuid(),:run,:workspace,'ORDER','INVALID:' || COALESCE(
                    NULLIF(e.raw_row->>'processor_transaction_id',''),NULLIF(e.raw_row->>'internal_payment_id',''),
                    NULLIF(e.raw_row->>'bank_entry_id',''),f.source_type || ':' || e.import_file_id::text || ':' || e.row_number::text),e.id,:now
                FROM import_row_error e JOIN import_file f ON f.id=e.import_file_id
                WHERE e.workspace_id=:workspace
                ON CONFLICT (run_id,work_type,work_key) DO NOTHING
                """)
                .param("run", runId)
                .param("workspace", workspaceId)
                .param("now", timestamp(now))
                .update();
        jdbc.sql(
                        """
                INSERT INTO reconciliation_work_item (id,run_id,workspace_id,work_type,work_key,created_at)
                SELECT gen_random_uuid(),:run,:workspace,'SETTLEMENT',external_batch_id,:now FROM settlement_batch WHERE workspace_id=:workspace
                ON CONFLICT (run_id,work_type,work_key) DO NOTHING
                """)
                .param("run", runId)
                .param("workspace", workspaceId)
                .param("now", timestamp(now))
                .update();
        jdbc.sql(
                        """
                INSERT INTO reconciliation_work_item (id,run_id,workspace_id,work_type,work_key,created_at)
                SELECT gen_random_uuid(),:run,:workspace,'SETTLEMENT','BANK_ONLY:' || b.bank_entry_id,:now
                FROM bank_settlement b WHERE b.workspace_id=:workspace AND NOT EXISTS (
                  SELECT 1 FROM processor_transaction p WHERE p.workspace_id=b.workspace_id AND p.settlement_batch_id=b.settlement_batch_id)
                ON CONFLICT (run_id,work_type,work_key) DO NOTHING
                """)
                .param("run", runId)
                .param("workspace", workspaceId)
                .param("now", timestamp(now))
                .update();
        jdbc.sql(
                        "UPDATE reconciliation_run SET total_work_items=(SELECT COUNT(*) FROM reconciliation_work_item WHERE run_id=:run),updated_at=:now WHERE id=:run")
                .param("run", runId)
                .param("now", timestamp(now))
                .update();
    }

    List<ReconciliationWorkItem> nextWork(
            UUID runId, String afterType, String afterKey, int limit) {
        return jdbc.sql(
                        """
                        SELECT id,run_id,workspace_id,work_type,work_key,source_error_id FROM reconciliation_work_item
                        WHERE run_id=:run AND processed=false
                          AND (work_type>:afterType OR (work_type=:afterType AND work_key>:afterKey))
                        ORDER BY work_type,work_key LIMIT :limit
                        """)
                .param("run", runId)
                .param("afterType", afterType)
                .param("afterKey", afterKey)
                .param("limit", limit)
                .query(
                        (rs, row) ->
                                new ReconciliationWorkItem(
                                        rs.getObject("id", UUID.class),
                                        rs.getObject("run_id", UUID.class),
                                        rs.getObject("workspace_id", UUID.class),
                                        rs.getString("work_type"),
                                        rs.getString("work_key"),
                                        rs.getObject("source_error_id", UUID.class)))
                .list();
    }

    EvaluatedWork evaluate(ReconciliationWorkItem work) {
        return new EvaluatedWork(work, policy.evaluate(work));
    }

    @Transactional
    public void write(List<? extends EvaluatedWork> evaluated) {
        Instant now = clock.instant();
        for (EvaluatedWork item : evaluated) {
            ReconciliationWorkItem work = item.work();
            ReconciliationDecision result = item.decision();
            UUID proposedResultId = UUID.randomUUID();
            jdbc.sql(
                            """
                    INSERT INTO reconciliation_result (id,run_id,workspace_id,work_item_id,result_scope,work_key,outcome,rule_code,explanation,
                    internal_transaction_id,processor_transaction_id,settlement_batch_id,bank_settlement_id,
                    internal_payment_reference,processor_transaction_reference,bank_entry_reference,gross_amount,processor_fee,
                    net_amount,bank_credit,difference,currency,internal_occurred_at,processor_event_at,bank_booked_at,
                    resolution_status,created_at,updated_at)
                    VALUES (:id,:run,:workspace,:work,:scope,:workKey,:outcome,:rule,:explanation,:internal,:processor,:settlement,:bank,
                    :internalReference,:processorReference,:bankReference,:gross,:fee,:net,:credit,:difference,:currency,
                    :internalAt,:processorAt,:bankAt,:resolution,:now,:now)
                    ON CONFLICT (run_id,work_item_id) DO NOTHING
                    """)
                    .param("id", proposedResultId)
                    .param("run", work.runId())
                    .param("workspace", work.workspaceId())
                    .param("work", work.id())
                    .param("scope", work.workType())
                    .param("workKey", work.workKey())
                    .param("outcome", result.outcome().name())
                    .param("rule", result.ruleCode())
                    .param("explanation", result.explanation())
                    .param("internal", result.internalTransactionId())
                    .param("processor", result.processorTransactionId())
                    .param("settlement", result.settlementBatchId())
                    .param("bank", result.bankSettlementId())
                    .param("internalReference", result.internalPaymentReference())
                    .param("processorReference", result.processorTransactionReference())
                    .param("bankReference", result.bankEntryReference())
                    .param("gross", result.grossAmount())
                    .param("fee", result.processorFee())
                    .param("net", result.netAmount())
                    .param("credit", result.bankCredit())
                    .param("difference", result.difference())
                    .param("currency", result.currency())
                    .param("internalAt", timestamp(result.internalOccurredAt()))
                    .param("processorAt", timestamp(result.processorEventAt()))
                    .param("bankAt", timestamp(result.bankBookedAt()))
                    .param("resolution", result.isMatched() ? "NOT_REQUIRED" : "OPEN")
                    .param("now", timestamp(now))
                    .update();
            jdbc.sql("UPDATE reconciliation_work_item SET processed=true WHERE id=:id")
                    .param("id", work.id())
                    .update();
            if (!result.isMatched()) {
                UUID resultId =
                        jdbc.sql(
                                        "SELECT id FROM reconciliation_result WHERE run_id=:run AND work_item_id=:work")
                                .param("run", work.runId())
                                .param("work", work.id())
                                .query(UUID.class)
                                .single();
                int auditInserted =
                        jdbc.sql(
                                        """
                        INSERT INTO audit_event (id,workspace_id,aggregate_type,aggregate_id,event_type,actor_type,details,occurred_at)
                        SELECT gen_random_uuid(),:workspace,'RECONCILIATION_RESULT',r.id,'EXCEPTION_CREATED','SYSTEM',
                        jsonb_build_object('outcome',:outcome,'ruleCode',:rule),:now
                        FROM reconciliation_result r WHERE r.run_id=:run AND r.work_item_id=:work
                        AND NOT EXISTS (SELECT 1 FROM audit_event a WHERE a.aggregate_id=r.id AND a.event_type='EXCEPTION_CREATED')
                        """)
                                .param("workspace", work.workspaceId())
                                .param("outcome", result.outcome().name())
                                .param("rule", result.ruleCode())
                                .param("now", timestamp(now))
                                .param("run", work.runId())
                                .param("work", work.id())
                                .update();
                if (auditInserted == 1) {
                    events.publishEvent(
                            new LifecycleEvent(
                                    "ExceptionCreated",
                                    work.workspaceId(),
                                    resultId,
                                    now,
                                    Map.of(
                                            "outcome",
                                            result.outcome().name(),
                                            "ruleCode",
                                            result.ruleCode())));
                }
            }
        }
    }

    @Transactional
    public void beforeJob(UUID runId, UUID workspaceId) {
        Instant now = clock.instant();
        jdbc.sql(
                        "UPDATE reconciliation_run SET status='RUNNING',started_at=COALESCE(started_at,:now),failure_message=NULL,updated_at=:now,version=version+1 WHERE id=:id AND workspace_id=:workspace")
                .param("now", timestamp(now))
                .param("id", runId)
                .param("workspace", workspaceId)
                .update();
        audit(workspaceId, runId, "RECONCILIATION_RUN_STARTED", Map.of());
        events.publishEvent(
                new LifecycleEvent("ReconciliationRunStarted", workspaceId, runId, now, Map.of()));
    }

    @Transactional
    public void afterJob(UUID runId, UUID workspaceId, boolean success, String failure) {
        Instant now = clock.instant();
        int processed = processed(runId);
        if (success) {
            Counts counts = counts(runId);
            jdbc.sql(
                            """
                    UPDATE reconciliation_run SET status='COMPLETED',processed_items=:processed,matched_count=:matched,
                    exception_count=:exceptions,completed_at=:now,updated_at=:now,failure_message=NULL,version=version+1
                    WHERE id=:id AND workspace_id=:workspace
                    """)
                    .param("processed", processed)
                    .param("matched", counts.matched())
                    .param("exceptions", counts.total() - counts.matched())
                    .param("now", timestamp(now))
                    .param("id", runId)
                    .param("workspace", workspaceId)
                    .update();
            audit(
                    workspaceId,
                    runId,
                    "RECONCILIATION_RUN_COMPLETED",
                    Map.of("results", counts.total(), "matched", counts.matched()));
            events.publishEvent(
                    new LifecycleEvent(
                            "ReconciliationRunCompleted",
                            workspaceId,
                            runId,
                            now,
                            Map.of(
                                    "results",
                                    Integer.toString(counts.total()),
                                    "matched",
                                    Integer.toString(counts.matched()))));
        } else {
            jdbc.sql(
                            """
                    UPDATE reconciliation_run SET status='FAILED',processed_items=:processed,completed_at=:now,updated_at=:now,
                    failure_message=:failure,version=version+1 WHERE id=:id AND workspace_id=:workspace
                    """)
                    .param("processed", processed)
                    .param("now", timestamp(now))
                    .param("failure", truncate(failure, 1000))
                    .param("id", runId)
                    .param("workspace", workspaceId)
                    .update();
            audit(
                    workspaceId,
                    runId,
                    "RECONCILIATION_RUN_FAILED",
                    Map.of("reason", truncate(failure, 300), "restartable", true));
            events.publishEvent(
                    new LifecycleEvent(
                            "ReconciliationRunFailed",
                            workspaceId,
                            runId,
                            now,
                            Map.of("reason", truncate(failure, 300), "restartable", "true")));
        }
    }

    private void assertImportsReady(UUID workspaceId) {
        List<String> missing =
                jdbc.sql(
                                """
                        SELECT source_type FROM (VALUES ('INTERNAL'),('PROCESSOR'),('BANK')) AS required(source_type)
                        WHERE NOT EXISTS (SELECT 1 FROM import_file i WHERE i.workspace_id=:workspace
                          AND i.source_type=required.source_type AND i.status IN ('COMPLETED','COMPLETED_WITH_ERRORS'))
                        """)
                        .param("workspace", workspaceId)
                        .query(String.class)
                        .list();
        if (!missing.isEmpty()) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "IMPORTS_NOT_READY",
                    "Completed imports are required for: " + String.join(", ", missing) + '.');
        }
    }

    private int processed(UUID runId) {
        return jdbc.sql(
                        "SELECT COUNT(*) FROM reconciliation_work_item WHERE run_id=:run AND processed=true")
                .param("run", runId)
                .query(Integer.class)
                .single();
    }

    private Counts counts(UUID runId) {
        return jdbc.sql(
                        """
                        SELECT COUNT(*) AS total,COUNT(*) FILTER (WHERE outcome IN ('MATCHED','REFUND_MATCHED')) AS matched
                        FROM reconciliation_result WHERE run_id=:run
                        """)
                .param("run", runId)
                .query((rs, row) -> new Counts(rs.getInt("total"), rs.getInt("matched")))
                .single();
    }

    private RunView runView(ResultSet rs, int liveProcessed) throws SQLException {
        int total = rs.getInt("total_work_items");
        int persistedProcessed = rs.getInt("processed_items");
        int processed = Math.max(persistedProcessed, liveProcessed);
        String status = rs.getString("status");
        double percent =
                total == 0
                        ? (status.equals("COMPLETED") ? 100.0 : 0.0)
                        : Math.min(100.0, Math.round(processed * 10_000.0 / total) / 100.0);
        Instant started = nullableInstant(rs, "started_at");
        Instant completed = nullableInstant(rs, "completed_at");
        Long duration =
                started == null
                        ? null
                        : Duration.between(started, completed == null ? clock.instant() : completed)
                                .toMillis();
        return new RunView(
                rs.getObject("id", UUID.class),
                status,
                total,
                processed,
                rs.getInt("matched_count"),
                rs.getInt("exception_count"),
                percent,
                started,
                completed,
                rs.getTimestamp("created_at").toInstant(),
                duration,
                status.equals("FAILED"),
                rs.getString("failure_message"));
    }

    private Instant nullableInstant(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private void audit(UUID workspaceId, UUID aggregateId, String event, Map<String, ?> details) {
        jdbc.sql(
                        "INSERT INTO audit_event (id,workspace_id,aggregate_type,aggregate_id,event_type,actor_type,details,occurred_at) VALUES (:id,:workspace,'RECONCILIATION_RUN',:aggregate,:event,'WORKSPACE_OPERATOR',CAST(:details AS jsonb),:now)")
                .param("id", UUID.randomUUID())
                .param("workspace", workspaceId)
                .param("aggregate", aggregateId)
                .param("event", event)
                .param("details", json(details))
                .param("now", timestamp(clock.instant()))
                .update();
    }

    private String json(Map<String, ?> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private String truncate(String value, int size) {
        String safe =
                value == null || value.isBlank()
                        ? "The batch job failed without a diagnostic message."
                        : value;
        return safe.length() <= size ? safe : safe.substring(0, size);
    }

    record EvaluatedWork(ReconciliationWorkItem work, ReconciliationDecision decision) {}

    private record BatchCounts(long reads, long writes, long skips) {}

    private record Counts(int total, int matched) {}

    private record RestartState(String status, Integer controlledFailureAfterChunks) {}
}
