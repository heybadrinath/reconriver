package io.github.heybadrinath.reconriver.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.heybadrinath.reconriver.shared.api.PageResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

@Service
public class AuditService {
    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {};
    private final JdbcClient jdbc;
    private final ObjectMapper objectMapper;

    AuditService(JdbcClient jdbc, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
    }

    public PageResponse<AuditEventView> list(
            UUID workspaceId, int page, int size, String eventType, String aggregateType) {
        int safePage = Math.max(0, page);
        int safeSize = Math.min(Math.max(1, size), 100);
        StringBuilder where = new StringBuilder("workspace_id=:workspace");
        if (eventType != null && !eventType.isBlank()) {
            where.append(" AND event_type=:event");
        }
        if (aggregateType != null && !aggregateType.isBlank()) {
            where.append(" AND aggregate_type=:aggregate");
        }
        JdbcClient.StatementSpec count =
                jdbc.sql("SELECT COUNT(*) FROM audit_event WHERE " + where)
                        .param("workspace", workspaceId);
        JdbcClient.StatementSpec query =
                jdbc.sql(
                                "SELECT * FROM audit_event WHERE "
                                        + where
                                        + " ORDER BY occurred_at DESC,id DESC LIMIT :limit OFFSET :offset")
                        .param("workspace", workspaceId)
                        .param("limit", safeSize)
                        .param("offset", safePage * safeSize);
        if (eventType != null && !eventType.isBlank()) {
            count = count.param("event", eventType);
            query = query.param("event", eventType);
        }
        if (aggregateType != null && !aggregateType.isBlank()) {
            count = count.param("aggregate", aggregateType);
            query = query.param("aggregate", aggregateType);
        }
        long total = count.query(Long.class).single();
        return PageResponse.of(query.query(this::view).list(), safePage, safeSize, total);
    }

    public List<AuditEventView> forAggregate(UUID workspaceId, UUID aggregateId) {
        return jdbc.sql(
                        "SELECT * FROM audit_event WHERE workspace_id=:workspace AND aggregate_id=:aggregate ORDER BY occurred_at,id")
                .param("workspace", workspaceId)
                .param("aggregate", aggregateId)
                .query(this::view)
                .list();
    }

    public AuditEventView view(ResultSet rs, int row) throws SQLException {
        try {
            return new AuditEventView(
                    rs.getObject("id", UUID.class),
                    rs.getString("aggregate_type"),
                    rs.getObject("aggregate_id", UUID.class),
                    rs.getString("event_type"),
                    rs.getString("actor_type"),
                    objectMapper.readValue(rs.getString("details"), DETAILS),
                    rs.getTimestamp("occurred_at").toInstant());
        } catch (java.io.IOException exception) {
            throw new SQLException("Invalid audit details JSON", exception);
        }
    }
}
