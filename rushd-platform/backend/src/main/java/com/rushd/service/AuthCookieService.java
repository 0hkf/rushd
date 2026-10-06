package com.rushd.service;

import com.rushd.config.AuthProperties;
import com.rushd.config.JwtProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;
import java.time.Duration;

@Service
public class AuthCookieService {
    public static final String ACCESS = "rawafed_access";
    public static final String REFRESH = "rawafed_refresh";
    private final AuthProperties settings;
    private final JwtProperties policy;
    public AuthCookieService(AuthProperties settings, JwtProperties policy) {
        this.settings = settings; this.policy = policy;
    }
    public String read(HttpServletRequest request, String name) {
        if (request.getCookies() == null) return null;
        String value = null;
        for (Cookie cookie : request.getCookies()) {
            if (cookie.getName().equals(name)) {
                if (value != null) return null; // Reject ambiguous duplicate credentials.
                value = cookie.getValue();
            }
        }
        return value;
    }
    public void issue(HttpServletResponse response, String access, String refresh) {
        write(response, ACCESS, access, "/", policy.accessTokenTtl());
        write(response, REFRESH, refresh, "/api/auth", policy.refreshTokenTtl());
    }
    public void clear(HttpServletResponse response) {
        write(response, ACCESS, "", "/", Duration.ZERO);
        write(response, REFRESH, "", "/api/auth", Duration.ZERO);
    }
    private void write(HttpServletResponse response, String name, String value, String path, Duration age) {
        response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(name, value).httpOnly(true)
                .secure(settings.cookieSecure()).sameSite(settings.cookieSameSite())
                .path(path).maxAge(age).build().toString());
    }
}
