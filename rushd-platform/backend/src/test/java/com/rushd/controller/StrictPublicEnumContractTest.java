package com.rushd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.Role;
import com.rushd.entity.User;
import com.rushd.repository.UserRepository;
import com.rushd.service.AuthCookieService;
import com.rushd.service.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class StrictPublicEnumContractTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;

    @ParameterizedTest @ValueSource(strings = {"type", "listingType", "rentalPeriod", "facade", "purpose", "status"})
    void createAndUpdateRejectEveryNonStringEnumWithoutWriting(String field) throws Exception {
        User admin = new User(); admin.setName("Enum test"); admin.setEmail(UUID.randomUUID() + "@example.invalid");
        admin.setPassword("unused-hash"); admin.setRole(Role.ADMIN); users.saveAndFlush(admin);
        Cookie access = new Cookie(AuthCookieService.ACCESS, jwt.generateAccessToken(admin.getEmail()));
        Map<String, Object> body = new LinkedHashMap<>(Map.of("title", "Enum contract", "type", "LAND",
                "city", "مكة", "district", "الياسمين", "price", "9007199254740993.01", "area", 500,
                "purpose", "RESIDENTIAL", "status", "ACTIVE", "listingType", "SALE"));
        var created = mvc.perform(post("/api/properties").with(csrf()).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(body)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.price").value("9007199254740993.01"))
                .andReturn();
        long id = json.readTree(created.getResponse().getContentAsString()).get("id").asLong();
        for (Object invalid : List.of(0, 1.0, true, List.of("LAND"), "0", "land", " UNKNOWN ")) {
            body.put(field, invalid);
            mvc.perform(post("/api/properties").with(csrf()).cookie(access).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
            mvc.perform(put("/api/properties/" + id).with(csrf()).cookie(access).contentType(MediaType.APPLICATION_JSON)
                    .content(json.writeValueAsString(body))).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/properties/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value("LAND")).andExpect(jsonPath("$.price").value("9007199254740993.01"));
    }

    @ParameterizedTest @ValueSource(strings = {"0", "2", "true", "\"0\"", "\"ADMIN\"", "\"SELLER\""})
    void registerRejectsOrdinalAndPrivilegedRoles(String role) throws Exception {
        mvc.perform(post("/api/auth/register").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Role test\",\"email\":\"role@example.invalid\",\"password\":\"Password123\",\"role\":" + role + "}"))
                .andExpect(status().isBadRequest());
    }
}
