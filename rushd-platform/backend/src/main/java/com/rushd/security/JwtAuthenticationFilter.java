package com.rushd.security;

import com.rushd.service.AuthCookieService;
import com.rushd.service.CustomUserDetailsService;
import com.rushd.service.JwtService;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.stereotype.Component;
import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private final JwtService jwtService;
    private final CustomUserDetailsService users;
    private final AuthCookieService cookies;
    public JwtAuthenticationFilter(JwtService jwtService, CustomUserDetailsService users, AuthCookieService cookies) {
        this.jwtService = jwtService; this.users = users; this.cookies = cookies;
    }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                             FilterChain chain) throws ServletException, IOException {
        String token = cookies.read(request, AuthCookieService.ACCESS);
        if (token != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String email = jwtService.validateAccessToken(token).getSubject();
                var user = users.loadUserByUsername(email);
                var authentication = new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (JwtException | IllegalArgumentException | UsernameNotFoundException ignored) {
                // Never log credentials. Protected routes resolve to the standard 401 response.
            }
        }
        chain.doFilter(request, response);
    }
}
