package com.rushd.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions", indexes = @Index(name = "idx_auth_session_user", columnList = "user_id"))
public class AuthSession {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false) private User user;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "revoked_at") private Instant revokedAt;
    protected AuthSession() {}
    public AuthSession(User user, Instant now) {
        this.id = UUID.randomUUID(); this.user = user; this.createdAt = now;
    }
    public UUID getId() { return id; }
    public User getUser() { return user; }
    public Instant getRevokedAt() { return revokedAt; }
    public void revoke(Instant now) { if (revokedAt == null) revokedAt = now; }
}
