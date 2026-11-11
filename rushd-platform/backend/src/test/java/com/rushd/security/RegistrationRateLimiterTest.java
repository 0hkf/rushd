package com.rushd.security;

import com.rushd.config.RegistrationRateLimitProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class RegistrationRateLimiterTest {
    @Test void fivePerAddressAndWindowBoundary() {
        var clock = new TokenLifecycleTest.MutableClock();
        clock.now = Instant.EPOCH;
        var limiter = new RegistrationRateLimiter(new RegistrationRateLimitProperties(5, Duration.ofMinutes(1), 2), clock);
        for (int i = 0; i < 5; i++) assertTrue(limiter.allow("one"));
        assertFalse(limiter.allow("one"));
        assertTrue(limiter.allow("two"));
        clock.now = clock.now.plusSeconds(59);
        assertFalse(limiter.allow("one"));
        clock.now = clock.now.plusSeconds(1);
        assertTrue(limiter.allow("one"));
    }

    @Test void fullCapacityFailsClosedOnlyForNewAddressesAndExpires() {
        var clock = new TokenLifecycleTest.MutableClock();
        clock.now = Instant.EPOCH;
        var limiter = new RegistrationRateLimiter(new RegistrationRateLimitProperties(5, Duration.ofMinutes(1), 1), clock);
        assertTrue(limiter.allow("one"));
        assertFalse(limiter.allow("two"));
        assertTrue(limiter.allow("one"));
        clock.now = clock.now.plusSeconds(60);
        assertTrue(limiter.allow("two"));
    }

    @Test void invalidPoliciesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new RegistrationRateLimitProperties(0, Duration.ofMinutes(1), 1));
        assertThrows(IllegalArgumentException.class, () -> new RegistrationRateLimitProperties(5, Duration.ZERO, 1));
        assertThrows(IllegalArgumentException.class, () -> new RegistrationRateLimitProperties(5, Duration.ofMinutes(1), 0));
    }
}
