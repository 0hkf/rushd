package com.rushd.security;

import com.rushd.entity.Role;
import com.rushd.entity.User;
import com.rushd.repository.UserRepository;
import com.rushd.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration-level tests for the JWT authentication filter and security
 * configuration.
 *
 * Uses a mocked UserRepository so no real database is required.
 */
@SpringBootTest
@AutoConfigureMockMvc
class JwtAuthenticationFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private User buildUser() {
        User u = new User();
        u.setEmail("test@rushd.com");
        u.setPassword("$2a$10$irrelevant-hash");
        u.setName("Test User");
        u.setRole(Role.BUYER);
        return u;
    }

    private String validToken() {
        return jwtService.generateAccessToken("test@rushd.com");
    }

    // -------------------------------------------------------------------------
    // Public endpoints remain accessible without a token
    // -------------------------------------------------------------------------

    @Test
    void registerEndpointIsPublic() throws Exception {
        // Empty body → 400 validation, NOT 401
        mockMvc.perform(post("/api/auth/register").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginEndpointIsPublic() throws Exception {
        // Empty body → 400 validation, NOT 401
        mockMvc.perform(post("/api/auth/login").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void healthEndpointIsPublic() throws Exception {
        mockMvc.perform(get("/health"))
                .andExpect(status().isOk());
    }

    // -------------------------------------------------------------------------
    // Register — validation returns 400 with field errors
    // -------------------------------------------------------------------------

    @Test
    void registerWithInvalidBodyReturns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/api/auth/register").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").isMap());
    }

    // -------------------------------------------------------------------------
    // Register — duplicate email returns 409
    // -------------------------------------------------------------------------

    @Test
    void duplicateEmailReturns409() throws Exception {
        when(userRepository.existsByEmail("buyer@rushd.local")).thenReturn(true);

        mockMvc.perform(post("/api/auth/register").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Buyer\",\"email\":\"buyer@rushd.local\",\"password\":\"Buyer123\",\"role\":\"BUYER\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409))
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").isString());
    }

    // -------------------------------------------------------------------------
    // Protected endpoint: no token → 401 with standardized body
    // -------------------------------------------------------------------------

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/protected-example"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Authentication is required"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/protected-example"));
    }

    // -------------------------------------------------------------------------
    // Protected endpoint: invalid token → 401
    // -------------------------------------------------------------------------

    @Test
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/protected-example")
                .cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, "this.is.not.a.valid.jwt")))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Protected endpoint: valid token → 404 (route doesn't exist, but NOT 401)
    // -------------------------------------------------------------------------

    @Test
    void validTokenAuthenticatesUser() throws Exception {
        when(userRepository.findByEmail("test@rushd.com"))
                .thenReturn(Optional.of(buildUser()));

        mockMvc.perform(get("/api/protected-example")
                .cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, validToken())))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // GET /api/auth/me — no token → 401
    // -------------------------------------------------------------------------

    @Test
    void meWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    // -------------------------------------------------------------------------
    // GET /api/auth/me — invalid token → 401
    // -------------------------------------------------------------------------

    @Test
    void meWithInvalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")
                .cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, "bad.token.value")))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // GET /api/auth/me — valid token → 200, no password field
    // -------------------------------------------------------------------------

    @Test
    void meWithValidTokenReturns200() throws Exception {
        when(userRepository.findByEmail("test@rushd.com"))
                .thenReturn(Optional.of(buildUser()));

        mockMvc.perform(get("/api/auth/me")
                .cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, validToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("test@rushd.com"))
                .andExpect(jsonPath("$.name").value("Test User"))
                .andExpect(jsonPath("$.role").value("BUYER"))
                // password MUST NOT appear in any response
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    // -------------------------------------------------------------------------
    // JwtService unit checks
    // -------------------------------------------------------------------------

    @Test
    void extractUsernameReturnsCorrectEmail() {
        String token = jwtService.generateAccessToken("user@example.com");
        assert "user@example.com".equals(jwtService.validateAccessToken(token).getSubject());
    }

    @Test
    void freshTokenIsNotExpired() {
        String token = jwtService.generateAccessToken("user@example.com");
        assert jwtService.validateAccessToken(token).getExpiration().toInstant().isAfter(java.time.Instant.now());
    }
}
