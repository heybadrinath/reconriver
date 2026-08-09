package io.github.heybadrinath.reconriver.workspace;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "demo_workspace")
class DemoWorkspace {
    @Id private UUID id;

    @Column(nullable = false, length = 24)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected DemoWorkspace() {}

    private DemoWorkspace(UUID id, Instant createdAt, Instant expiresAt) {
        this.id = id;
        this.status = "ACTIVE";
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
        this.updatedAt = createdAt;
    }

    static DemoWorkspace active(UUID id, Instant createdAt, Instant expiresAt) {
        return new DemoWorkspace(id, createdAt, expiresAt);
    }

    void expire(Instant now) {
        if (!"ACTIVE".equals(status) || expiresAt.isAfter(now)) {
            throw new IllegalStateException("Only an elapsed active workspace can expire");
        }
        status = "EXPIRED";
        updatedAt = now;
    }

    UUID id() {
        return id;
    }

    long version() {
        return version;
    }
}
