package com.rushd.security;

import jakarta.servlet.http.Cookie;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.Role;
import com.rushd.entity.User;
import com.rushd.repository.UserRepository;
import com.rushd.service.AuthCookieService;
import com.rushd.service.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// Isolated context: MockMvc's csrf() postprocessor replaces the filter's real repository.
// This suite deliberately never uses it: exercise actual cookie + XOR-masked header delivery.
@SpringBootTest(properties = "spring.application.name=real-cookie-csrf-test")
@AutoConfigureMockMvc
@Transactional
class CookieCsrfIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired PasswordEncoder passwords;
    @Test void realCookieAndMaskedHeaderProtectStateChanges() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Invalid CSRF token"));
        var bootstrap = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String token = mapper.readTree(bootstrap.getResponse().getContentAsString()).get("token").asText();
        Cookie cookie = bootstrap.getResponse().getCookie("XSRF-TOKEN");
        assertNotNull(cookie); assertTrue(cookie.isHttpOnly());
        mvc.perform(post("/api/auth/register").cookie(cookie).header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"CSRF user\",\"email\":\"csrf@example.com\",\"password\":\"Password123\",\"role\":\"BUYER\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(put("/api/properties/1").cookie(cookie).header("X-XSRF-TOKEN", token)
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/logout").cookie(cookie).header("X-XSRF-TOKEN", "wrong"))
                .andExpect(status().isForbidden());
    }

    @Test void repeatedJwtRequestsPreserveCsrfProofButStillRejectMissingOrWrongProof() throws Exception {
        User user = createUser("repeat-csrf@example.com");
        Cookie access = new Cookie(AuthCookieService.ACCESS, jwt.generateAccessToken(user.getEmail()));
        CsrfProof proof = bootstrap();

        MvcResult me = mvc.perform(get("/api/auth/me").cookie(access, proof.cookie()))
                .andExpect(status().isOk()).andReturn();
        assertNull(me.getResponse().getCookie("XSRF-TOKEN"), "ordinary JWT GET must not delete the CSRF cookie");

        for (int i = 0; i < 2; i++) {
            MvcResult validated = mvc.perform(post("/api/property-needs/validate")
                            .cookie(access, proof.cookie()).header("X-XSRF-TOKEN", proof.header())
                            .contentType(MediaType.APPLICATION_JSON).content(needsBody()))
                    .andExpect(status().isOk()).andReturn();
            assertNull(validated.getResponse().getCookie("XSRF-TOKEN"),
                    "ordinary JWT POST must leave the existing cookie and cached proof usable");
        }

        mvc.perform(post("/api/property-needs/validate").cookie(access, proof.cookie())
                        .header("X-XSRF-TOKEN", "wrong").contentType(MediaType.APPLICATION_JSON).content(needsBody()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.message").value("Invalid CSRF token"));
        mvc.perform(post("/api/property-needs/validate").cookie(access, proof.cookie())
                        .contentType(MediaType.APPLICATION_JSON).content(needsBody()))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/property-needs/validate").cookie(access)
                        .header("X-XSRF-TOKEN", proof.header()).contentType(MediaType.APPLICATION_JSON).content(needsBody()))
                .andExpect(status().isForbidden());
    }

    @Test void explicitLoginRefreshAndLogoutStillClearCsrfCookies() throws Exception {
        createUser("lifecycle-csrf@example.com");
        CsrfProof beforeLogin = bootstrap();
        MvcResult login = mvc.perform(post("/api/auth/login").cookie(beforeLogin.cookie())
                        .header("X-XSRF-TOKEN", beforeLogin.header()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"lifecycle-csrf@example.com\",\"password\":\"Password123\"}"))
                .andExpect(status().isOk()).andReturn();
        assertCsrfDeleted(login);

        CsrfProof afterLogin = bootstrap();
        assertNotEquals(beforeLogin.cookie().getValue(), afterLogin.cookie().getValue());
        Cookie access = cookie(login, AuthCookieService.ACCESS);
        Cookie refresh = cookie(login, AuthCookieService.REFRESH);
        MvcResult renewed = mvc.perform(post("/api/auth/refresh").cookie(access, refresh, afterLogin.cookie())
                        .header("X-XSRF-TOKEN", afterLogin.header()))
                .andExpect(status().isOk()).andReturn();
        assertCsrfDeleted(renewed);
        Cookie newRefresh = cookie(renewed, AuthCookieService.REFRESH);
        assertNotEquals(refresh.getValue(), newRefresh.getValue());

        CsrfProof afterRefresh = bootstrap();
        assertNotEquals(afterLogin.cookie().getValue(), afterRefresh.cookie().getValue());
        MvcResult logout = mvc.perform(post("/api/auth/logout")
                        .cookie(cookie(renewed, AuthCookieService.ACCESS), newRefresh, afterRefresh.cookie())
                        .header("X-XSRF-TOKEN", afterRefresh.header()))
                .andExpect(status().isNoContent()).andReturn();
        assertCsrfDeleted(logout);
        assertEquals(0, cookie(logout, AuthCookieService.ACCESS).getMaxAge());
        assertEquals(0, cookie(logout, AuthCookieService.REFRESH).getMaxAge());
        CsrfProof afterLogout = bootstrap();
        mvc.perform(post("/api/auth/refresh").cookie(newRefresh, afterLogout.cookie())
                        .header("X-XSRF-TOKEN", afterLogout.header()))
                .andExpect(status().isUnauthorized());
    }

    private CsrfProof bootstrap() throws Exception {
        MvcResult result = mvc.perform(get("/api/auth/csrf")).andExpect(status().isOk()).andReturn();
        String header = mapper.readTree(result.getResponse().getContentAsString()).get("token").asText();
        return new CsrfProof(cookie(result, "XSRF-TOKEN"), header);
    }

    private Cookie cookie(MvcResult result, String name) {
        Cookie cookie = result.getResponse().getCookie(name);
        assertNotNull(cookie);
        return cookie;
    }

    private void assertCsrfDeleted(MvcResult result) {
        assertEquals(0, cookie(result, "XSRF-TOKEN").getMaxAge());
    }

    private User createUser(String email) {
        User user = new User();
        user.setEmail(email);
        user.setName("Real CSRF test");
        user.setRole(Role.BUYER);
        user.setPassword(passwords.encode("Password123"));
        return users.saveAndFlush(user);
    }

    private String needsBody() {
        return "{\"listingType\":\"SALE\",\"type\":\"LAND\",\"maxBudget\":\"500000\"}";
    }

    private record CsrfProof(Cookie cookie, String header) {}
}
