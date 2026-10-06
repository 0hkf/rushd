package com.rushd.security;
import com.rushd.config.AuthRateLimitProperties;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimiterTest {
    @Test void limiterIsTemporaryBoundedAndSeparatesEndpoints() {
        var clock = new TokenLifecycleTest.MutableClock();
        var window = Duration.ofMinutes(1);
        var limiter = new AuthRateLimiter(new AuthRateLimitProperties(2, 3, window, 2), clock);
        assertTrue(limiter.allow("one", true)); assertTrue(limiter.allow("one", true));
        assertFalse(limiter.allow("one", true));
        assertTrue(limiter.allow("one", false));
        assertFalse(limiter.allow("two", true));
        clock.now = clock.now.plus(window);
        assertTrue(limiter.allow("two", true));
    }
}
