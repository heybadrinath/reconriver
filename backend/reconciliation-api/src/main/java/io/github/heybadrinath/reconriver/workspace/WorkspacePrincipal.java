package io.github.heybadrinath.reconriver.workspace;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

public record WorkspacePrincipal(UUID workspaceId, UUID sessionId, Instant expiresAt)
        implements Serializable {}
