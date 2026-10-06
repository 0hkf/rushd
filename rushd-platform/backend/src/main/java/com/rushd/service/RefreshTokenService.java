package com.rushd.service;

import com.rushd.config.JwtProperties;
import com.rushd.entity.*;
import com.rushd.exception.InvalidRefreshTokenException;
import com.rushd.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Clock;
import java.time.Instant;
import java.util.*;

@Service
public class RefreshTokenService {
    public record Issued(String credential, User user) {}
    private final AuthSessionRepository sessions;
    private final RefreshTokenRepository tokens;
    private final JwtProperties policy;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public RefreshTokenService(AuthSessionRepository sessions, RefreshTokenRepository tokens,
                               JwtProperties policy, Clock clock) {
        this.sessions = sessions; this.tokens = tokens; this.policy = policy; this.clock = clock;
    }

    @Transactional
    public Issued createSession(User user) {
        AuthSession session = sessions.save(new AuthSession(user, clock.instant()));
        return issue(session);
    }

    // Revocation on replay must COMMIT even though the HTTP request fails.
    @Transactional(noRollbackFor = InvalidRefreshTokenException.class)
    public Issued rotate(String credential) {
        String hash = hashCredential(credential);
        UUID family = tokens.findSessionIdByHash(hash).orElseThrow(InvalidRefreshTokenException::new);
        AuthSession session = sessions.lockById(family).orElseThrow(InvalidRefreshTokenException::new);
        // Read token state only after obtaining the family lock; all rotations/logout serialize here.
        RefreshToken token = tokens.findByTokenHash(hash).orElseThrow(InvalidRefreshTokenException::new);
        Instant now = clock.instant();
        if (session.getRevokedAt() != null) throw new InvalidRefreshTokenException();
        if (token.getConsumedAt() != null) {
            session.revoke(now);
            throw new InvalidRefreshTokenException();
        }
        if (!token.getExpiresAt().isAfter(now)) throw new InvalidRefreshTokenException();
        Issued next = issue(session);
        RefreshToken replacement = tokens.findByTokenHash(hashCredential(next.credential())).orElseThrow();
        token.consume(now, replacement.getId());
        return next;
    }

    @Transactional
    public void revoke(String credential) {
        if (credential == null) return;
        try {
            tokens.findSessionIdByHash(hashCredential(credential))
                    .flatMap(sessions::lockById).ifPresent(session -> session.revoke(clock.instant()));
        } catch (InvalidRefreshTokenException ignored) {
            // Logout is idempotent and always removes browser cookies.
        }
    }

    private Issued issue(AuthSession session) {
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String raw = "r1." + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Instant now = clock.instant();
        tokens.saveAndFlush(new RefreshToken(session, hashCredential(raw), now, now.plus(policy.refreshTokenTtl())));
        // Associated user is obtained through the FK in this transaction; deleted users cannot refresh.
        User user = session.getUser();
        user.getEmail(); // Initialize the associated identity while the transaction is open.
        return new Issued(raw, user);
    }

    public static String hashCredential(String raw) {
        if (raw == null || !raw.matches("r1\\.[A-Za-z0-9_-]{43}")) throw new InvalidRefreshTokenException();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
