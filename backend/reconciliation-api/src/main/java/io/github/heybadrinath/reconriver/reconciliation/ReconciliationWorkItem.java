package io.github.heybadrinath.reconriver.reconciliation;

import java.util.UUID;

record ReconciliationWorkItem(
        UUID id,
        UUID runId,
        UUID workspaceId,
        String workType,
        String workKey,
        UUID sourceErrorId) {}
