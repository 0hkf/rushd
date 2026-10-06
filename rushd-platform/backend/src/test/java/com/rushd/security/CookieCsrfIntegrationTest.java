package com.rushd.security;

import jakarta.servlet.http.Cookie;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
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
}
