package com.rushd.security;
import com.rushd.config.AuthRateLimitProperties;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
public class AuthRateLimitFilter extends OncePerRequestFilter {
    private final AuthRateLimiter limiter;
    private final SecurityErrorWriter errors;
    private final AuthRateLimitProperties policy;
    public AuthRateLimitFilter(AuthRateLimiter limiter, SecurityErrorWriter errors, AuthRateLimitProperties policy) {
        this.limiter = limiter; this.errors = errors; this.policy = policy;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain) throws ServletException, IOException {
        boolean login = request.getRequestURI().substring(request.getContextPath().length()).equals("/api/auth/login");
        boolean refresh = request.getServletPath().equals("/api/auth/refresh");
        if ("POST".equals(request.getMethod()) && (login || refresh)
                && !limiter.allow(request.getRemoteAddr(), login)) {
            response.setHeader("Retry-After", Long.toString(Math.max(1, policy.window().toSeconds())));
            errors.write(request, response, 429, "Too many authentication requests");
            return;
        }
        chain.doFilter(request, response);
    }
}
