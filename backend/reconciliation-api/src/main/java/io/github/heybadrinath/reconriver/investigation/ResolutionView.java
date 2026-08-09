package io.github.heybadrinath.reconriver.investigation;

import java.time.Instant;
import java.util.UUID;

public record ResolutionView(UUID id, String action, String note, Instant createdAt) {}
