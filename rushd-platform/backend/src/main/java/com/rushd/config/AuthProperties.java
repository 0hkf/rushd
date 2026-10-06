package com.rushd.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import java.net.URI;

@ConfigurationProperties("security.auth")
public record AuthProperties(boolean cookieSecure, String cookieSameSite, String frontendOrigin) {
    public AuthProperties {
        if (!java.util.Set.of("Lax", "Strict", "None").contains(cookieSameSite))
            throw new IllegalArgumentException("AUTH_COOKIE_SAME_SITE must be Lax, Strict or None");
        if ("None".equals(cookieSameSite) && !cookieSecure)
            throw new IllegalArgumentException("SameSite=None requires secure cookies");
        URI origin = URI.create(frontendOrigin);
        if (origin.getHost() == null || !java.util.Set.of("http", "https").contains(origin.getScheme())
                || origin.getRawQuery() != null || origin.getRawFragment() != null
                || origin.getUserInfo() != null || (origin.getPath() != null && !origin.getPath().isEmpty()))
            throw new IllegalArgumentException("FRONTEND_ORIGIN must be one explicit HTTP(S) origin without a path");
    }
}
