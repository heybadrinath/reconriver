package io.github.heybadrinath.reconriver.shared.events;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record LifecycleEvent(
        String type,
        UUID workspaceId,
        UUID aggregateId,
        Instant occurredAt,
        Map<String, String> attributes) {

    public LifecycleEvent {
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
