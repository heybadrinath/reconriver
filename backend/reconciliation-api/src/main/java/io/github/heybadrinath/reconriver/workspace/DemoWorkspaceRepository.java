package io.github.heybadrinath.reconriver.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface DemoWorkspaceRepository extends JpaRepository<DemoWorkspace, UUID> {
    List<DemoWorkspace> findByStatusAndExpiresAtLessThanEqual(String status, Instant expiresAt);
}
