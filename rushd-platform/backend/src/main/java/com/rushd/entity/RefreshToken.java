package com.rushd.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_family", columnList = "session_id"),
        @Index(name = "idx_refresh_expiry", columnList = "expires_at")
})
public class RefreshToken {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false) private AuthSession session;
    @Column(name = "token_hash", nullable = false, unique = true, length = 64) private String tokenHash;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false) private Instant expiresAt;
    @Column(name = "consumed_at") private Instant consumedAt;
    @Column(name = "replaced_by_token_id") private UUID replacedByTokenId;
    protected RefreshToken() {}
    public RefreshToken(AuthSession session, String hash, Instant now, Instant expiresAt) {
        this.id = UUID.randomUUID(); this.session = session; this.tokenHash = hash;
        this.createdAt = now; this.expiresAt = expiresAt;
    }
    public UUID getId() { return id; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getConsumedAt() { return consumedAt; }
    public String getTokenHash() { return tokenHash; }
    public AuthSession getSession() { return session; }
    public void consume(Instant now, UUID replacement) {
        consumedAt = now; replacedByTokenId = replacement;
    }
}
