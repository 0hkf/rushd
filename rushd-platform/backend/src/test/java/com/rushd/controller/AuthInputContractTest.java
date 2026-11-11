package com.rushd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthInputContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;

    @ParameterizedTest @ValueSource(strings = {"a", "ع"})
    void nameAtStorageLimitIsAcceptedButNextCharacterIsRejectedBeforeSave(String letter) throws Exception {
        register(letter.repeat(255), email(), "Password123").andExpect(status().isCreated());
        String rejected = email();
        register(letter.repeat(256), rejected, "Password123")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message.name").exists());
        assertFalse(users.existsByEmail(rejected));
    }

    @Test void maximumValidEmailAndNextCharacterRespectStorageLimit() throws Exception {
        String local = "a".repeat(64), label = "b".repeat(63);
        String atLimit = local + "@" + label + "." + label + "." + "c".repeat(58) + ".com";
        register("Email boundary", atLimit, "Password123").andExpect(status().isCreated());
        String tooLong = atLimit.replace("c".repeat(58), "c".repeat(59));
        register("Email boundary", tooLong, "Password123")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message.email").exists());
        assertFalse(users.existsByEmail(tooLong));
    }

    @Test void unicodeLowercasingCannotExpandEmailPastTheStorageLimit() throws Exception {
        String label = "b".repeat(63);
        String email = "İ".repeat(64) + "@" + label + "." + label + "." + "c".repeat(58) + ".com";
        register("Normalized email boundary", email, "Password123")
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message.email").exists());
    }

    @ParameterizedTest @ValueSource(strings = {"name", "email"})
    void requiredFieldsStillRejectNullAndBlank(String field) throws Exception {
        for (String value : new String[] {null, "", "   "}) {
            var body = new java.util.LinkedHashMap<String, Object>(Map.of(
                    "name", "Valid user", "email", email(), "password", "Password123", "role", "BUYER"));
            body.put(field, value);
            mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message." + field).exists());
        }
    }

    @ParameterizedTest @ValueSource(strings = {"login", "register"})
    void unsupportedMethodKeeps405AndAllowHeader(String endpoint) throws Exception {
        mvc.perform(get("/api/auth/" + endpoint)).andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("POST")))
                .andExpect(jsonPath("$.status").value(405)).andExpect(jsonPath("$.trace").doesNotExist());
    }

    @ParameterizedTest @ValueSource(strings = {"login", "register"})
    void unsupportedContentTypeKeeps415(String endpoint) throws Exception {
        mvc.perform(post("/api/auth/" + endpoint).with(csrf()).contentType(MediaType.TEXT_PLAIN).content("not json"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.trace").doesNotExist());
    }

    @Test void malformedJsonRemains400AndMissingCsrfRemains403() throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest @ValueSource(strings = {"a", "ع"})
    void byteBoundaryIsExactAndLoginNeverTruncates(String letter) throws Exception {
        String password = letter.repeat(letter.equals("ع") ? 36 : 72);
        String account = email();
        register("Password boundary", account, password).andExpect(status().isCreated());
        mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", account, "password", password))))
                .andExpect(status().isOk());
        for (String wrong : new String[] {password + "x", "z" + password.substring(1)}) {
            mvc.perform(post("/api/auth/login").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(Map.of("email", account, "password", wrong))))
                    .andExpect(status().isUnauthorized());
        }
        String rejected = email();
        register("Long password", rejected, password + "x").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.password").exists());
        assertFalse(users.existsByEmail(rejected));
    }

    private org.springframework.test.web.servlet.ResultActions register(String name, String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("name", name, "email", email, "password", password, "role", "BUYER"))));
    }
    private String email() { return "auth-contract-" + UUID.randomUUID() + "@example.invalid"; }
}
