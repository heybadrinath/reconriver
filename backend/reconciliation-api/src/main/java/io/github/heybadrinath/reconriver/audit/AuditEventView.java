package io.github.heybadrinath.reconriver.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditEventView(
        UUID id,
        String aggregateType,
        UUID aggregateId,
        String eventType,
        String actorType,
        Map<String, Object> details,
        Instant occurredAt) {}
