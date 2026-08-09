package io.github.heybadrinath.reconriver.workspace;

import static io.github.heybadrinath.reconriver.shared.config.SqlTime.timestamp;

import io.github.heybadrinath.reconriver.shared.config.ReconRiverProperties;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.batch.core.job.JobInstance;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoSessionService {
    public static final String UPLOAD_WARNING =
            "Use synthetic data only. Do not upload real payment, customer, card or bank information.";

    private final JdbcClient jdbc;
    private final DemoWorkspaceRepository workspaces;
    private final TokenHasher tokenHasher;
    private final Clock clock;
    private final ReconRiverProperties properties;
    private final JobRepository jobRepository;

    DemoSessionService(
            JdbcClient jdbc,
            DemoWorkspaceRepository workspaces,
            TokenHasher tokenHasher,
            Clock clock,
            ReconRiverProperties properties,
            JobRepository jobRepository) {
        this.jdbc = jdbc;
        this.workspaces = workspaces;
        this.tokenHasher = tokenHasher;
        this.clock = clock;
        this.properties = properties;
        this.jobRepository = jobRepository;
    }

    @Transactional
    public SessionCreated create() {
        Instant now = clock.instant();
        Instant expiresAt = now.plus(properties.security().sessionTtl());
        UUID workspaceId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        String token = tokenHasher.newToken();
        workspaces.saveAndFlush(DemoWorkspace.active(workspaceId, now, expiresAt));
        jdbc.sql(
                        "INSERT INTO demo_session_token (id,workspace_id,token_hash,created_at,expires_at) VALUES (:id,:workspace,:hash,:now,:expires)")
                .param("id", sessionId)
                .param("workspace", workspaceId)
                .param("hash", tokenHasher.hash(token))
                .param("now", timestamp(now))
                .param("expires", timestamp(expiresAt))
                .update();
        audit(
                workspaceId,
                workspaceId,
                "WORKSPACE_CREATED",
                Map.of("expiresAt", expiresAt.toString()));
        return new SessionCreated(token, workspaceId, expiresAt, UPLOAD_WARNING);
    }

    @Transactional
    public Optional<WorkspacePrincipal> authenticate(String token) {
        if (token == null || token.length() < 32 || token.length() > 256) {
            return Optional.empty();
        }
        Instant now = clock.instant();
        Optional<WorkspacePrincipal> principal =
                jdbc.sql(
                                """
                        SELECT t.workspace_id,t.id,t.expires_at
                        FROM demo_session_token t JOIN demo_workspace w ON w.id=t.workspace_id
                        WHERE t.token_hash=:hash AND t.expires_at>:now AND w.expires_at>:now AND w.status='ACTIVE'
                        """)
                        .param("hash", tokenHasher.hash(token))
                        .param("now", timestamp(now))
                        .query(this::principal)
                        .optional();
        principal.ifPresent(
                value ->
                        jdbc.sql("UPDATE demo_session_token SET last_used_at=:now WHERE id=:id")
                                .param("now", timestamp(now))
                                .param("id", value.sessionId())
                                .update());
        return principal;
    }

    @Scheduled(cron = "${reconriver.cleanup.cron:0 */5 * * * *}")
    @Transactional
    public int removeExpiredWorkspaces() {
        Instant now = clock.instant();
        workspaces
                .findByStatusAndExpiresAtLessThanEqual("ACTIVE", now)
                .forEach(workspace -> workspace.expire(now));
        workspaces.flush();
        int deleted =
                jdbc.sql("DELETE FROM demo_workspace WHERE status='EXPIRED' AND expires_at<=:now")
                        .param("now", timestamp(now))
                        .update();
        Instant publicationCutoff = now.minus(properties.security().sessionTtl());
        jdbc.sql(
                        "DELETE FROM event_publication_archive WHERE completion_date IS NOT NULL AND completion_date<=:cutoff")
                .param("cutoff", timestamp(publicationCutoff))
                .update();
        jdbc.sql(
                        "DELETE FROM event_publication WHERE completion_date IS NOT NULL AND completion_date<=:cutoff")
                .param("cutoff", timestamp(publicationCutoff))
                .update();
        removeExpiredBatchMetadata(publicationCutoff);
        return deleted;
    }

    private void removeExpiredBatchMetadata(Instant cutoff) {
        List<Long> instances =
                jdbc.sql(
                                """
                        SELECT ji.job_instance_id
                        FROM batch_job_instance ji
                        WHERE EXISTS (
                          SELECT 1 FROM batch_job_execution je
                          WHERE je.job_instance_id=ji.job_instance_id
                        )
                        AND NOT EXISTS (
                          SELECT 1 FROM batch_job_execution je
                          WHERE je.job_instance_id=ji.job_instance_id
                            AND je.status NOT IN ('COMPLETED','FAILED','STOPPED','ABANDONED')
                        )
                        AND (SELECT MAX(COALESCE(je.end_time,je.last_updated,je.create_time))
                             FROM batch_job_execution je
                             WHERE je.job_instance_id=ji.job_instance_id)<=:cutoff
                        AND NOT EXISTS (
                          SELECT 1 FROM import_file i JOIN batch_job_execution je
                            ON je.job_execution_id=i.job_execution_id
                          WHERE je.job_instance_id=ji.job_instance_id
                        )
                        AND NOT EXISTS (
                          SELECT 1 FROM reconciliation_run r JOIN batch_job_execution je
                            ON je.job_execution_id=r.job_execution_id
                          WHERE je.job_instance_id=ji.job_instance_id
                        )
                        ORDER BY ji.job_instance_id
                        LIMIT 100
                        """)
                        .param("cutoff", timestamp(cutoff))
                        .query(Long.class)
                        .list();
        for (Long instanceId : instances) {
            JobInstance instance = jobRepository.getJobInstance(instanceId);
            if (instance != null) {
                jobRepository.deleteJobInstance(instance);
            }
        }
    }

    private WorkspacePrincipal principal(ResultSet resultSet, int row) throws SQLException {
        return new WorkspacePrincipal(
                resultSet.getObject("workspace_id", UUID.class),
                resultSet.getObject("id", UUID.class),
                resultSet.getTimestamp("expires_at").toInstant());
    }

    private void audit(UUID workspaceId, UUID aggregateId, String event, Map<String, ?> details) {
        jdbc.sql(
                        "INSERT INTO audit_event (id,workspace_id,aggregate_type,aggregate_id,event_type,actor_type,details,occurred_at) VALUES (:id,:workspace,'WORKSPACE',:aggregate,:event,'SYSTEM',CAST(:details AS jsonb),:now)")
                .param("id", UUID.randomUUID())
                .param("workspace", workspaceId)
                .param("aggregate", aggregateId)
                .param("event", event)
                .param("details", toJson(details))
                .param("now", timestamp(clock.instant()))
                .update();
    }

    private String toJson(Map<String, ?> values) {
        return values.entrySet().stream()
                .map(
                        entry ->
                                "\""
                                        + entry.getKey().replace("\"", "")
                                        + "\":\""
                                        + String.valueOf(entry.getValue()).replace("\"", "\\\"")
                                        + "\"")
                .collect(java.util.stream.Collectors.joining(",", "{", "}"));
    }

    public record SessionCreated(
            String token, UUID workspaceId, Instant expiresAt, String warning) {}
}
