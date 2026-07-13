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

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration-level tests for the JWT authentication filter and security configuration.
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
        u.setPassword("$2a$10$irrelevant-hash"); // not used in filter path
        u.setName("Test User");
        u.setRole(Role.BUYER);
        return u;
    }

    private String validToken() {
        return jwtService.generateToken("test@rushd.com", "BUYER");
    }

    // -------------------------------------------------------------------------
    // Public endpoints remain accessible without a token
    // -------------------------------------------------------------------------

    @Test
    void registerEndpointIsPublic() throws Exception {
        // Sending an intentionally invalid body; we expect 400 (validation), not 401
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginEndpointIsPublic() throws Exception {
        // Sending an intentionally invalid body; we expect 400 (validation), not 401
        mockMvc.perform(post("/api/auth/login")
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
    // Protected endpoint: no token → 401
    // -------------------------------------------------------------------------

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/protected-example"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.message").value("Authentication is required"));
    }

    // -------------------------------------------------------------------------
    // Protected endpoint: invalid token → 401
    // -------------------------------------------------------------------------

    @Test
    void invalidTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/protected-example")
                        .header("Authorization", "Bearer this.is.not.a.valid.jwt"))
                .andExpect(status().isUnauthorized());
    }

    // -------------------------------------------------------------------------
    // Protected endpoint: valid token → 200 (or whatever the controller returns)
    //   — we just check it is NOT 401
    // -------------------------------------------------------------------------

    @Test
    void validTokenAuthenticatesUser() throws Exception {
        when(userRepository.findByEmail("test@rushd.com"))
                .thenReturn(Optional.of(buildUser()));

        String token = validToken();

        // Any protected URL — 404 is fine, it means auth passed and the route just doesn't exist
        mockMvc.perform(get("/api/protected-example")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // -------------------------------------------------------------------------
    // JwtService unit checks
    // -------------------------------------------------------------------------

    @Test
    void extractUsernameReturnsCorrectEmail() {
        String token = jwtService.generateToken("user@example.com", "SELLER");
        String username = jwtService.extractUsername(token);
        assert "user@example.com".equals(username);
    }

    @Test
    void freshTokenIsNotExpired() {
        String token = jwtService.generateToken("user@example.com", "SELLER");
        assert !jwtService.isTokenExpired(token);
    }
}
