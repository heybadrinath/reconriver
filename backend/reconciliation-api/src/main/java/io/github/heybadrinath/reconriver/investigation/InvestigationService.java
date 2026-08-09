package io.github.heybadrinath.reconriver.investigation;

import static io.github.heybadrinath.reconriver.reconciliation.ResultsService.RESULT_PROJECTION;
import static io.github.heybadrinath.reconriver.shared.config.SqlTime.timestamp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.heybadrinath.reconriver.audit.AuditService;
import io.github.heybadrinath.reconriver.reconciliation.Outcome;
import io.github.heybadrinath.reconriver.reconciliation.ResultView;
import io.github.heybadrinath.reconriver.reconciliation.ResultsService;
import io.github.heybadrinath.reconriver.shared.api.ApiException;
import io.github.heybadrinath.reconriver.shared.api.PageResponse;
import io.github.heybadrinath.reconriver.shared.events.LifecycleEvent;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InvestigationService {
    private final JdbcClient jdbc;
    private final ResultsService results;
    private final AuditService audit;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher events;

    InvestigationService(
            JdbcClient jdbc,
            ResultsService results,
            AuditService audit,
            Clock clock,
            ObjectMapper objectMapper,
            ApplicationEventPublisher events) {
        this.jdbc = jdbc;
        this.results = results;
        this.audit = audit;
        this.clock = clock;
        this.objectMapper = objectMapper;
        this.events = events;
    }

    public PageResponse<ResultView> list(
            UUID workspaceId, int page, int size, UUID runId, String outcome, String status) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        StringBuilder where =
                new StringBuilder(
                        "r.workspace_id=:workspace AND r.resolution_status<>'NOT_REQUIRED'");
        Map<String, Object> params = new HashMap<>();
        params.put("workspace", workspaceId);
        if (runId != null) {
            where.append(" AND r.run_id=:run");
            params.put("run", runId);
        }
        if (outcome != null && !outcome.isBlank()) {
            try {
                Outcome.valueOf(outcome.toUpperCase());
            } catch (IllegalArgumentException exception) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_OUTCOME",
                        "The requested outcome filter is not supported.");
            }
            where.append(" AND r.outcome=:outcome");
            params.put("outcome", outcome.toUpperCase());
        }
        if (status != null && !status.isBlank()) {
            String normalized = status.toUpperCase();
            if (!Set.of("OPEN", "REOPENED", "RESOLVED").contains(normalized)) {
                throw new ApiException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_RESOLUTION_STATUS",
                        "status must be OPEN, REOPENED or RESOLVED.");
            }
            if (normalized.equals("OPEN")) {
                where.append(" AND r.resolution_status IN ('OPEN','REOPENED')");
            } else {
                where.append(" AND r.resolution_status=:status");
                params.put("status", normalized);
            }
        }
        long total =
                bind(
                                jdbc.sql(
                                        "SELECT COUNT(*) FROM reconciliation_result r WHERE "
                                                + where),
                                params)
                        .query(Long.class)
                        .single();
        params.put("limit", safeSize);
        params.put("offset", safePage * safeSize);
        List<ResultView> items =
                bind(
                                jdbc.sql(
                                        RESULT_PROJECTION
                                                + " WHERE "
                                                + where
                                                + " ORDER BY r.created_at DESC,r.id DESC LIMIT :limit OFFSET :offset"),
                                params)
                        .query(results::resultView)
                        .list();
        return PageResponse.of(items, safePage, safeSize, total);
    }

    public ExceptionDetailView get(UUID workspaceId, UUID exceptionId) {
        ResultView result = results.get(workspaceId, exceptionId);
        if (result.resolutionStatus().equals("NOT_REQUIRED")) {
            throw new ApiException(
                    HttpStatus.NOT_FOUND,
                    "EXCEPTION_NOT_FOUND",
                    "The requested result is matched and is not an exception.");
        }
        List<ResolutionView> resolutions =
                jdbc.sql(
                                """
                        SELECT id,action,note,created_at FROM exception_resolution
                        WHERE workspace_id=:workspace AND result_id=:result ORDER BY created_at,id
                        """)
                        .param("workspace", workspaceId)
                        .param("result", exceptionId)
                        .query(this::resolution)
                        .list();
        return new ExceptionDetailView(
                result, resolutions, audit.forAggregate(workspaceId, exceptionId));
    }

    @Transactional
    public ResultView resolve(UUID workspaceId, UUID exceptionId, String note) {
        State state = lock(workspaceId, exceptionId);
        if (!state.resolutionStatus().equals("OPEN")
                && !state.resolutionStatus().equals("REOPENED")) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EXCEPTION_ALREADY_RESOLVED",
                    "This exception is already resolved.");
        }
        Instant now = clock.instant();
        jdbc.sql(
                        """
                UPDATE reconciliation_result SET original_outcome=COALESCE(original_outcome,outcome),outcome='MANUALLY_RESOLVED',
                resolution_status='RESOLVED',updated_at=:now,version=version+1 WHERE id=:id AND workspace_id=:workspace
                """)
                .param("now", timestamp(now))
                .param("id", exceptionId)
                .param("workspace", workspaceId)
                .update();
        appendResolution(workspaceId, exceptionId, "RESOLVED", note, now);
        appendAudit(workspaceId, exceptionId, "EXCEPTION_RESOLVED", note, now);
        events.publishEvent(
                new LifecycleEvent(
                        "ExceptionResolved",
                        workspaceId,
                        exceptionId,
                        now,
                        Map.of("note", note.strip())));
        return results.get(workspaceId, exceptionId);
    }

    @Transactional
    public ResultView reopen(UUID workspaceId, UUID exceptionId, String note) {
        State state = lock(workspaceId, exceptionId);
        if (!state.resolutionStatus().equals("RESOLVED")) {
            throw new ApiException(
                    HttpStatus.CONFLICT,
                    "EXCEPTION_NOT_RESOLVED",
                    "Only a resolved exception can be reopened.");
        }
        Instant now = clock.instant();
        jdbc.sql(
                        """
                UPDATE reconciliation_result SET outcome=COALESCE(original_outcome,outcome),resolution_status='REOPENED',
                updated_at=:now,version=version+1 WHERE id=:id AND workspace_id=:workspace
                """)
                .param("now", timestamp(now))
                .param("id", exceptionId)
                .param("workspace", workspaceId)
                .update();
        appendResolution(workspaceId, exceptionId, "REOPENED", note, now);
        appendAudit(workspaceId, exceptionId, "EXCEPTION_REOPENED", note, now);
        events.publishEvent(
                new LifecycleEvent(
                        "ExceptionReopened",
                        workspaceId,
                        exceptionId,
                        now,
                        Map.of("note", note.strip())));
        return results.get(workspaceId, exceptionId);
    }

    private State lock(UUID workspaceId, UUID exceptionId) {
        return jdbc.sql(
                        "SELECT resolution_status FROM reconciliation_result WHERE id=:id AND workspace_id=:workspace AND resolution_status<>'NOT_REQUIRED' FOR UPDATE")
                .param("id", exceptionId)
                .param("workspace", workspaceId)
                .query((rs, row) -> new State(rs.getString("resolution_status")))
                .optional()
                .orElseThrow(
                        () ->
                                new ApiException(
                                        HttpStatus.NOT_FOUND,
                                        "EXCEPTION_NOT_FOUND",
                                        "No exception exists with that ID in this workspace."));
    }

    private void appendResolution(
            UUID workspaceId, UUID resultId, String action, String note, Instant now) {
        jdbc.sql(
                        "INSERT INTO exception_resolution (id,result_id,workspace_id,action,note,created_at) VALUES (:id,:result,:workspace,:action,:note,:now)")
                .param("id", UUID.randomUUID())
                .param("result", resultId)
                .param("workspace", workspaceId)
                .param("action", action)
                .param("note", note.strip())
                .param("now", timestamp(now))
                .update();
    }

    private void appendAudit(
            UUID workspaceId, UUID resultId, String event, String note, Instant now) {
        jdbc.sql(
                        "INSERT INTO audit_event (id,workspace_id,aggregate_type,aggregate_id,event_type,actor_type,details,occurred_at) VALUES (:id,:workspace,'RECONCILIATION_RESULT',:result,:event,'WORKSPACE_OPERATOR',CAST(:details AS jsonb),:now)")
                .param("id", UUID.randomUUID())
                .param("workspace", workspaceId)
                .param("result", resultId)
                .param("event", event)
                .param("details", json(Map.of("note", note.strip())))
                .param("now", timestamp(now))
                .update();
    }

    private ResolutionView resolution(ResultSet rs, int row) throws SQLException {
        return new ResolutionView(
                rs.getObject("id", UUID.class),
                rs.getString("action"),
                rs.getString("note"),
                rs.getTimestamp("created_at").toInstant());
    }

    private JdbcClient.StatementSpec bind(
            JdbcClient.StatementSpec statement, Map<String, Object> parameters) {
        for (Map.Entry<String, Object> parameter : parameters.entrySet()) {
            statement = statement.param(parameter.getKey(), parameter.getValue());
        }
        return statement;
    }

    private String json(Map<String, ?> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record State(String resolutionStatus) {}
}
