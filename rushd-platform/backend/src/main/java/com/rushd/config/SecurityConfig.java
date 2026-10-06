package com.rushd.config;

import com.rushd.security.*;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.csrf.*;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.*;
import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final JwtAuthenticationFilter authentication;
    private final AuthProperties settings;
    private final SecurityErrorWriter errors;
    public SecurityConfig(JwtAuthenticationFilter authentication, AuthProperties settings, SecurityErrorWriter errors) {
        this.authentication = authentication; this.settings = settings; this.errors = errors;
    }
    @Bean public org.springframework.security.authentication.AuthenticationManager authenticationManager(
            org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
    @Bean public PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(); }
    @Bean public CsrfTokenRepository csrfTokenRepository() {
        var repository = new CookieCsrfTokenRepository();
        repository.setCookieCustomizer(builder -> builder.httpOnly(true).secure(settings.cookieSecure())
                .sameSite(settings.cookieSameSite()).path("/"));
        return repository;
    }
    @Bean public CorsConfigurationSource corsConfigurationSource() {
        var config = new CorsConfiguration();
        config.setAllowedOrigins(List.of(settings.frontendOrigin()));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
        config.setAllowCredentials(true);
        var source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config); return source;
    }
    @Bean public SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrf, AuthRateLimitFilter rateLimit) throws Exception {
        http.cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(config -> config.csrfTokenRepository(csrf))
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                    .requestMatchers("/health", "/api/auth/csrf", "/api/auth/register", "/api/auth/login",
                            "/api/auth/refresh", "/api/auth/logout").permitAll()
                    .requestMatchers(HttpMethod.GET, "/api/properties", "/api/properties/{id}").permitAll()
                    .anyRequest().authenticated())
            .exceptionHandling(config -> config
                    .authenticationEntryPoint((request, response, exception) -> errors.write(request, response, 401, "Authentication is required"))
                    .accessDeniedHandler((request, response, exception) -> errors.write(request, response, 403, exception instanceof CsrfException ? "Invalid CSRF token" : "Insufficient permission")))
            .headers(headers -> headers
                    .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'; base-uri 'none'"))
                    .referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .frameOptions(frame -> frame.deny()))
            .addFilterBefore(authentication, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(rateLimit, JwtAuthenticationFilter.class)
            .httpBasic(basic -> basic.disable()).formLogin(form -> form.disable()).logout(logout -> logout.disable());
        return http.build();
    }
}
