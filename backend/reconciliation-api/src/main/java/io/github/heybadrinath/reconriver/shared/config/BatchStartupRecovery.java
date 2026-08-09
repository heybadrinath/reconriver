package io.github.heybadrinath.reconriver.shared.config;

import static io.github.heybadrinath.reconriver.shared.config.SqlTime.timestamp;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

@Component
public class BatchStartupRecovery {
    private static final String RECOVERY_MESSAGE =
            "The service restarted while this batch was active. Its durable checkpoint is preserved; retry to continue safely.";

    private final JdbcClient jdbc;
    private final JobRepository jobRepository;
    private final JobOperator jobOperator;
    private final Clock clock;

    BatchStartupRecovery(
            JdbcClient jdbc, JobRepository jobRepository, JobOperator jobOperator, Clock clock) {
        this.jdbc = jdbc;
        this.jobRepository = jobRepository;
        this.jobOperator = jobOperator;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void recoverInterruptedBatches() {
        recoverImports();
        recoverRuns();
    }

    private void recoverImports() {
        List<ActiveBatch> active =
                jdbc.sql(
                                """
                        SELECT id,workspace_id,job_execution_id
                        FROM import_file
                        WHERE status IN ('VALIDATING','QUEUED','RUNNING')
                        ORDER BY created_at,id
                        """)
                        .query(this::activeBatch)
                        .list();
        for (ActiveBatch batch : active) {
            recoverExecution(batch.jobExecutionId());
            Instant now = clock.instant();
            int claimed =
                    jdbc.sql(
                                    """
                            UPDATE import_file
                            SET status='FAILED',failure_message=:failure,updated_at=:now,version=version+1
                            WHERE id=:id AND status IN ('VALIDATING','QUEUED','RUNNING')
                            """)
                            .param("failure", RECOVERY_MESSAGE)
                            .param("now", timestamp(now))
                            .param("id", batch.id())
                            .update();
            if (claimed == 1) {
                audit(
                        batch.workspaceId(),
                        "IMPORT",
                        batch.id(),
                        "IMPORT_INTERRUPTED_BY_RESTART",
                        now);
            }
        }
    }

    private void recoverRuns() {
        List<ActiveBatch> active =
                jdbc.sql(
                                """
                        SELECT id,workspace_id,job_execution_id
                        FROM reconciliation_run
                        WHERE status IN ('QUEUED','STARTING','RUNNING')
                        ORDER BY created_at,id
                        """)
                        .query(this::activeBatch)
                        .list();
        for (ActiveBatch batch : active) {
            recoverExecution(batch.jobExecutionId());
            Instant now = clock.instant();
            int claimed =
                    jdbc.sql(
                                    """
                            UPDATE reconciliation_run
                            SET status='FAILED',failure_message=:failure,completed_at=:now,updated_at=:now,version=version+1
                            WHERE id=:id AND status IN ('QUEUED','STARTING','RUNNING')
                            """)
                            .param("failure", RECOVERY_MESSAGE)
                            .param("now", timestamp(now))
                            .param("id", batch.id())
                            .update();
            if (claimed == 1) {
                audit(
                        batch.workspaceId(),
                        "RECONCILIATION_RUN",
                        batch.id(),
                        "RECONCILIATION_RUN_INTERRUPTED_BY_RESTART",
                        now);
            }
        }
    }

    private void recoverExecution(Long executionId) {
        if (executionId == null) {
            return;
        }
        JobExecution execution = jobRepository.getJobExecution(executionId);
        if (execution != null && execution.isRunning()) {
            jobOperator.recover(execution);
        }
    }

    private ActiveBatch activeBatch(java.sql.ResultSet resultSet, int row)
            throws java.sql.SQLException {
        Number executionId = (Number) resultSet.getObject("job_execution_id");
        return new ActiveBatch(
                resultSet.getObject("id", UUID.class),
                resultSet.getObject("workspace_id", UUID.class),
                executionId == null ? null : executionId.longValue());
    }

    private void audit(
            UUID workspaceId,
            String aggregateType,
            UUID aggregateId,
            String eventType,
            Instant now) {
        jdbc.sql(
                        """
                        INSERT INTO audit_event
                          (id,workspace_id,aggregate_type,aggregate_id,event_type,actor_type,details,occurred_at)
                        VALUES
                          (:id,:workspace,:aggregateType,:aggregate,:event,'SYSTEM',CAST(:details AS jsonb),:now)
                        """)
                .param("id", UUID.randomUUID())
                .param("workspace", workspaceId)
                .param("aggregateType", aggregateType)
                .param("aggregate", aggregateId)
                .param("event", eventType)
                .param("details", "{\"recovery\":\"startup\"}")
                .param("now", timestamp(now))
                .update();
    }

    private record ActiveBatch(UUID id, UUID workspaceId, Long jobExecutionId) {}
}
