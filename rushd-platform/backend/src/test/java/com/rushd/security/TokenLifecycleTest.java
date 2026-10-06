package com.rushd.security;

import com.rushd.config.*;
import com.rushd.entity.*;
import com.rushd.repository.*;
import com.rushd.service.*;
import com.rushd.exception.InvalidRefreshTokenException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TokenLifecycleTest.TestClockConfiguration.class)
class TokenLifecycleTest {
    static class MutableClock extends Clock {
        Instant now = Instant.parse("2026-10-06T00:00:00Z");
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
    @TestConfiguration static class TestClockConfiguration {
        @Bean @Primary MutableClock testClock() { return new MutableClock(); }
    }
    @Autowired MockMvc mvc;
    @Autowired JwtProperties policy;
    @Autowired JwtService jwt;
    @Autowired RefreshTokenService refresh;
    @Autowired RefreshTokenRepository tokens;
    @Autowired AuthSessionRepository sessions;
    @Autowired UserRepository users;
    @Autowired PasswordEncoder passwords;
    @Autowired MutableClock clock;
    private User user;

    @BeforeEach void setup() {
        tokens.deleteAll(); sessions.deleteAll();
        users.findByEmail("lifecycle@example.com").ifPresent(users::delete);
        clock.now = Instant.parse("2026-10-06T00:00:00Z");
        user = new User(); user.setEmail("lifecycle@example.com"); user.setName("Lifecycle");
        user.setRole(Role.BUYER); user.setPassword(passwords.encode("Password123"));
        user = users.saveAndFlush(user);
    }

    private MvcResult login() throws Exception {
        return mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"lifecycle@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("BUYER"))
                .andExpect(jsonPath("$.token").doesNotExist()).andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(jsonPath("$.user.password").doesNotExist()).andReturn();
    }
    private Cookie cookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertNotNull(cookie); return cookie;
    }

    @Test void authoritativeDefaultPolicyAndAllExpirationsAgree() throws Exception {
        assertEquals(Duration.ofHours(1), policy.accessTokenTtl());
        assertEquals(Duration.ofDays(3), policy.refreshTokenTtl());
        var result = login();
        Cookie access = cookie(result, AuthCookieService.ACCESS), raw = cookie(result, AuthCookieService.REFRESH);
        var claims = jwt.validateAccessToken(access.getValue());
        assertEquals(policy.accessTokenTtl(), Duration.between(claims.getIssuedAt().toInstant(), claims.getExpiration().toInstant()));
        assertEquals(policy.accessTokenTtl().toSeconds(), access.getMaxAge());
        assertEquals(policy.refreshTokenTtl().toSeconds(), raw.getMaxAge());
        assertTrue(access.isHttpOnly()); assertTrue(raw.isHttpOnly());
        assertEquals("/", access.getPath()); assertEquals("/api/auth", raw.getPath());
        assertEquals("Lax", raw.getAttribute("SameSite"));
        RefreshToken stored = tokens.findAll().get(0);
        assertEquals(clock.instant().plus(policy.refreshTokenTtl()), stored.getExpiresAt());
        assertNotEquals(raw.getValue(), stored.getTokenHash());
        assertEquals(RefreshTokenService.hashCredential(raw.getValue()), stored.getTokenHash());
        assertEquals(Set.of("sub", "iat", "exp", "token_type"), claims.keySet());
    }

    @Test void invalidWeakConfigurationFailsFastAndProductionCookiesAreSecure() {
        assertThrows(IllegalArgumentException.class, () -> new JwtProperties(policy.accessTokenTtl(), policy.refreshTokenTtl(), "abc123"));
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties(false, "None", "https://example.com"));
        assertThrows(IllegalArgumentException.class, () -> new AuthProperties(true, "Lax", "*"));
        var service = new AuthCookieService(new AuthProperties(true, "Strict", "https://example.com"), policy);
        var response = new org.springframework.mock.web.MockHttpServletResponse();
        service.issue(response, jwt.generateAccessToken(user.getEmail()), "test");
        for (String header : response.getHeaders("Set-Cookie")) {
            assertTrue(header.contains("Secure")); assertTrue(header.contains("HttpOnly")); assertTrue(header.contains("SameSite=Strict"));
        }
    }

    @Test void expiryBoundaryAndSignatureAndTokenTypeAreValidated() {
        String access = jwt.generateAccessToken(user.getEmail());
        clock.now = clock.now.plus(policy.accessTokenTtl());
        assertThrows(JwtException.class, () -> jwt.validateAccessToken(access));
        String wrongType = Jwts.builder().setSubject(user.getEmail()).claim("token_type", "refresh")
                .setExpiration(Date.from(clock.now.plus(policy.accessTokenTtl())))
                .signWith(Keys.hmacShaKeyFor(policy.secret().getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        assertThrows(JwtException.class, () -> jwt.validateAccessToken(wrongType));
        String forged = Jwts.builder().setSubject(user.getEmail()).claim("token_type", "access")
                .setExpiration(Date.from(clock.now.plus(policy.accessTokenTtl())))
                .signWith(Keys.hmacShaKeyFor("different-test-key-of-at-least-32-bytes".getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
        assertThrows(JwtException.class, () -> jwt.validateAccessToken(forged));
    }

    @Test void rotationIsSingleUseAndReplayRevokesFamily() throws Exception {
        Cookie a = cookie(login(), AuthCookieService.REFRESH);
        var result = mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(a))
                .andExpect(status().isOk()).andReturn();
        Cookie b = cookie(result, AuthCookieService.REFRESH);
        assertNotEquals(a.getValue(), b.getValue());
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(a)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(b)).andExpect(status().isUnauthorized());
        assertNotNull(sessions.findAll().get(0).getRevokedAt());
    }

    @Test void expiredAndUnknownRefreshCredentialsAreRejected() throws Exception {
        Cookie raw = cookie(login(), AuthCookieService.REFRESH);
        clock.now = clock.now.plus(policy.refreshTokenTtl());
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(raw)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(
                new Cookie(AuthCookieService.REFRESH, "r1." + "A".repeat(43)))).andExpect(status().isUnauthorized());
    }

    @Test void tokenTypesCannotBeConfusedAndBearerIsNotAccepted() throws Exception {
        var result = login();
        Cookie raw = cookie(result, AuthCookieService.REFRESH);
        Cookie access = cookie(result, AuthCookieService.ACCESS);
        mvc.perform(get("/api/auth/me").cookie(new Cookie(AuthCookieService.ACCESS, raw.getValue())))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(new Cookie(AuthCookieService.REFRESH, access.getValue())))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + access.getValue()))
                .andExpect(status().isUnauthorized());
    }

    @Test void logoutRevokesSessionAndDeletesCookiesWithMatchingAttributes() throws Exception {
        var result = login(); Cookie raw = cookie(result, AuthCookieService.REFRESH);
        var logout = mvc.perform(post("/api/auth/logout").with(csrf()).cookie(raw))
                .andExpect(status().isNoContent()).andReturn();
        for (String name : List.of(AuthCookieService.ACCESS, AuthCookieService.REFRESH)) {
            Cookie deleted = cookie(logout, name), issued = cookie(result, name);
            assertEquals(0, deleted.getMaxAge()); assertEquals(issued.getPath(), deleted.getPath());
            assertEquals(issued.getSecure(), deleted.getSecure());
            assertEquals(issued.getAttribute("SameSite"), deleted.getAttribute("SameSite"));
        }
        mvc.perform(post("/api/auth/refresh").with(csrf()).cookie(raw)).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").with(csrf())).andExpect(status().isNoContent());
    }

    @Test void corsAndSecurityHeadersAreExplicit() throws Exception {
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5173")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Content-Type,X-XSRF-TOKEN"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"))
                .andExpect(header().string("Access-Control-Allow-Credentials", "true"));
        mvc.perform(options("/api/auth/login").header("Origin", "https://evil.example")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
        mvc.perform(get("/health")).andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY")).andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"));
        mvc.perform(get("/health").secure(true)).andExpect(header().exists("Strict-Transport-Security"));
    }

    @Test void concurrentRotationHasOnlyOneWinner() throws Exception {
        String raw = refresh.createSession(user).credential();
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Boolean> attempt = () -> {
            start.await();
            try { refresh.rotate(raw); return true; }
            catch (InvalidRefreshTokenException expected) { return false; }
        };
        try {
            Future<Boolean> one = executor.submit(attempt), two = executor.submit(attempt);
            start.countDown();
            assertNotEquals(one.get(10, TimeUnit.SECONDS), two.get(10, TimeUnit.SECONDS));
            assertEquals(2, tokens.count());
            assertNotNull(sessions.findAll().get(0).getRevokedAt());
        } finally { executor.shutdownNow(); }
    }
}
