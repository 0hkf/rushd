package com.rushd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

@ConfigurationProperties("security.auth.registration-rate-limit")
public record RegistrationRateLimitProperties(@DefaultValue("5") int requests,
        @DefaultValue("1m") Duration window, @DefaultValue("10000") int maxClients) {
    public RegistrationRateLimitProperties {
        if (requests < 1 || window == null || window.isZero() || window.isNegative() || maxClients < 1) {
            throw new IllegalArgumentException("Registration rate-limit policy must be positive");
        }
    }
}
