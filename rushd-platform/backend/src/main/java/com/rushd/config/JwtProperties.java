package com.rushd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

@ConfigurationProperties("security.jwt")
public record JwtProperties(Duration accessTokenTtl, Duration refreshTokenTtl, String secret) {
    @Override public String toString() {
        return "JwtProperties[accessTokenTtl=" + accessTokenTtl + ", refreshTokenTtl=" + refreshTokenTtl + ", secret=<redacted>]";
    }
    public JwtProperties {
        if (accessTokenTtl == null || accessTokenTtl.isNegative() || accessTokenTtl.isZero()
                || accessTokenTtl.getNano() != 0 || refreshTokenTtl == null || refreshTokenTtl.isNegative()
                || refreshTokenTtl.isZero() || refreshTokenTtl.getNano() != 0) {
            throw new IllegalArgumentException("Token TTLs must be positive whole-second durations");
        }
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalArgumentException("JWT_SECRET must contain at least 32 UTF-8 bytes; configure a high-entropy secret");
        }
    }
}
