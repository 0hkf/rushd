package com.rushd.security;

import com.rushd.config.AuthRateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.*;
import java.time.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class AuthRateLimitFilterTest {
    @Test void highRiskEndpointsReturn429WithoutTrustingForwardedAddresses() throws Exception {
        var policy = new AuthRateLimitProperties(2, 3, Duration.ofMinutes(1), 10);
        var limiter = new AuthRateLimiter(policy, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));
        var filter = new AuthRateLimitFilter(limiter, new SecurityErrorWriter(new ObjectMapper()), policy);
        AtomicInteger passed = new AtomicInteger();
        for (int attempt = 0; attempt < 3; attempt++) {
            var request = new MockHttpServletRequest("POST", "/api/auth/login");
            request.setRemoteAddr("127.0.0.1");
            request.addHeader("X-Forwarded-For", "untrusted-" + attempt);
            var response = new MockHttpServletResponse();
            filter.doFilter(request, response, (req, res) -> passed.incrementAndGet());
            if (attempt == 2) {
                assertEquals(429, response.getStatus());
                assertNotNull(response.getHeader("Retry-After"));
                assertEquals(429, new ObjectMapper().readTree(response.getContentAsString()).get("status").asInt());
            }
        }
        assertEquals(2, passed.get());
        var unrelated = new MockHttpServletRequest("POST", "/api/properties");
        filter.doFilter(unrelated, new MockHttpServletResponse(), (req, res) -> passed.incrementAndGet());
        assertEquals(3, passed.get());
    }
}
