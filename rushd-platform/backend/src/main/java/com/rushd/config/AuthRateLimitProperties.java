package com.rushd.config;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

@ConfigurationProperties("security.auth.rate-limit")
public record AuthRateLimitProperties(@DefaultValue("10") int loginRequests,
        @DefaultValue("30") int refreshRequests, @DefaultValue("1m") Duration window,
        @DefaultValue("10000") int maxClients) {
    public AuthRateLimitProperties {
        if (loginRequests < 1 || refreshRequests < 1 || window == null
                || window.isNegative() || window.isZero() || maxClients < 1)
            throw new IllegalArgumentException("Authentication rate-limit policy must be positive");
    }
}
