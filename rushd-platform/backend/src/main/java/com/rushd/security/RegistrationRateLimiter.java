package com.rushd.security;

import com.rushd.config.RegistrationRateLimitProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/** Per-process, bounded registration quota; its capacity is separate from login and refresh. */
@Component
public class RegistrationRateLimiter {
    private record Window(Instant start, int requests) {}
    private final Map<String, Window> clients = new HashMap<>();
    private final RegistrationRateLimitProperties policy;
    private final Clock clock;

    public RegistrationRateLimiter(RegistrationRateLimitProperties policy, Clock clock) {
        this.policy = policy;
        this.clock = clock;
    }

    public synchronized boolean allow(String address) {
        Instant now = clock.instant();
        clients.entrySet().removeIf(entry -> !entry.getValue().start().plus(policy.window()).isAfter(now));
        Window current = clients.get(address);
        if (current == null) {
            if (clients.size() >= policy.maxClients()) return false;
            current = new Window(now, 0);
        }
        if (current.requests() >= policy.requests()) return false;
        clients.put(address, new Window(current.start(), current.requests() + 1));
        return true;
    }
}
