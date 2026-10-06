package com.rushd.security;
import com.rushd.config.AuthRateLimitProperties;
import org.springframework.stereotype.Component;
import java.time.*;
import java.util.*;

@Component
public class AuthRateLimiter {
    private record Window(Instant start, int requests) {}
    private final Map<String, Window> clients = new HashMap<>();
    private final AuthRateLimitProperties policy;
    private final Clock clock;
    public AuthRateLimiter(AuthRateLimitProperties policy, Clock clock) { this.policy = policy; this.clock = clock; }
    // Bounded, synchronized, per-process fixed window. No trusting client-supplied forwarded IP headers.
    public synchronized boolean allow(String address, boolean login) {
        Instant now = clock.instant();
        clients.entrySet().removeIf(entry -> !entry.getValue().start().plus(policy.window()).isAfter(now));
        String key = (login ? "login:" : "refresh:") + address;
        Window window = clients.get(key);
        if (window == null) {
            if (clients.size() >= policy.maxClients()) return false; // fail closed when bounded capacity is full
            window = new Window(now, 0);
        }
        int limit = login ? policy.loginRequests() : policy.refreshRequests();
        if (window.requests() >= limit) return false;
        clients.put(key, new Window(window.start(), window.requests() + 1));
        return true;
    }
}
