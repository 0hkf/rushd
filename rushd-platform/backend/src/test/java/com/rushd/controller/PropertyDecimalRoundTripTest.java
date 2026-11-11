package com.rushd.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.Property;
import com.rushd.entity.Role;
import com.rushd.entity.User;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import com.rushd.service.AuthCookieService;
import com.rushd.service.JwtService;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyDecimalRoundTripTest {
    private static final String[] FIELDS = {"price", "area", "streetWidth", "latitude", "longitude"};
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private PropertyRepository properties;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private EntityManager entities;
    private Cookie adminAccess;

    @BeforeEach
    void setup() {
        User admin = new User();
        admin.setName("Decimal roundtrip");
        admin.setEmail("decimal-roundtrip@example.com");
        admin.setPassword("unused-hash");
        admin.setRole(Role.ADMIN);
        users.saveAndFlush(admin);
        adminAccess = new Cookie(AuthCookieService.ACCESS, jwt.generateAccessToken(admin.getEmail()));
    }

    @Test
    void createUpdateAndAllPropertyReadsPreserveExactPlainDecimalStrings() throws Exception {
        Map<String, Object> body = body();
        body.put("price", "99999999999999999.99");
        body.put("area", "9999999999.99");
        body.put("streetWidth", "99999999.99");
        body.put("latitude", "-90.0000000");
        body.put("longitude", "-180.0000000");
        JsonNode created = create(body);
        assertDecimals(created, body);
        long id = created.get("id").asLong();
        freshPersistenceContext();
        assertDecimals(read(id), body);
        assertDecimals(findListing(id, false), body);
        assertDecimals(findListing(id, true), body);

        body.put("price", "0.01");
        body.put("area", "0.01");
        body.put("streetWidth", "0.01");
        body.put("latitude", "0.0000000");
        body.put("longitude", "-0.0000001");
        JsonNode updated = mapper.readTree(mvc.perform(put("/api/properties/{id}", id).with(csrf()).cookie(adminAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertDecimals(updated, body);
        freshPersistenceContext();
        assertDecimals(read(id), body);
        Property stored = properties.findById(id).orElseThrow();
        assertEquals(0, new BigDecimal("0.01").compareTo(stored.getPrice()));
        assertEquals(0, new BigDecimal("-0.0000001").compareTo(stored.getLongitude()));
    }

    @Test
    void positiveCoordinateBoundariesAndNullableValuesRoundTripWithoutChangingRequiredFields() throws Exception {
        Map<String, Object> body = body();
        body.put("latitude", "90.0000000");
        body.put("longitude", "180.0000000");
        JsonNode created = create(body);
        long id = created.get("id").asLong();
        freshPersistenceContext();
        assertDecimals(read(id), body);
        body.put("latitude", null);
        body.put("longitude", null);
        body.put("streetWidth", null);
        mvc.perform(put("/api/properties/{id}", id).with(csrf()).cookie(adminAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk());
        freshPersistenceContext();
        assertDecimals(read(id), body);
        assertDecimals(findListing(id, false), body);
        assertDecimals(findListing(id, true), body);
    }

    @ParameterizedTest
    @ValueSource(strings = {"price", "area", "streetWidth", "latitude", "longitude"})
    void invalidUpdateLeavesAllDecimalsAndUpdatedAtUnchangedInAFreshRead(String invalidField) throws Exception {
        Map<String, Object> original = body();
        original.put("streetWidth", "20.25");
        original.put("latitude", "21.5101000");
        original.put("longitude", "39.9962000");
        long id = create(original).get("id").asLong();
        freshPersistenceContext();
        Property before = properties.findById(id).orElseThrow();
        LocalDateTime updatedAt = before.getUpdatedAt();
        long publisherId = before.getPublisher().getId();
        long count = properties.count();
        Map<String, Object> attempted = body();
        attempted.put("title", "Should not be saved");
        attempted.put("price", "100");
        attempted.put("area", "200");
        attempted.put("streetWidth", "30");
        attempted.put("latitude", "-10");
        attempted.put("longitude", "-20");
        attempted.put(invalidField, "0E+2147483647");
        mvc.perform(put("/api/properties/{id}", id).with(csrf()).cookie(adminAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(attempted)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message." + invalidField).exists());
        freshPersistenceContext();
        Property after = properties.findById(id).orElseThrow();
        assertEquals(updatedAt, after.getUpdatedAt());
        assertEquals(publisherId, after.getPublisher().getId());
        assertEquals("Decimal roundtrip property", after.getTitle());
        assertEquals(count, properties.count());
        assertDecimals(read(id), original);
    }

    private JsonNode create(Map<String, Object> body) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/properties").with(csrf()).cookie(adminAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode read(long id) throws Exception {
        return mapper.readTree(mvc.perform(get("/api/properties/{id}", id)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    private JsonNode findListing(long id, boolean admin) throws Exception {
        var request = get(admin ? "/api/admin/properties" : "/api/properties");
        if (admin) request.cookie(adminAccess);
        JsonNode response = mapper.readTree(mvc.perform(request).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        for (JsonNode item : response.get("content")) {
            if (item.get("id").asLong() == id) return item;
        }
        throw new AssertionError("Created property missing from listing");
    }

    private void assertDecimals(JsonNode property, Map<String, Object> expected) {
        for (String field : FIELDS) {
            JsonNode value = property.get(field);
            assertNotNull(value, field);
            if (expected.get(field) == null) {
                assertTrue(value.isNull(), field);
            } else {
                assertTrue(value.isTextual(), field);
                assertTrue(value.asText().matches("-?\\d+(\\.\\d+)?"), field + " must be a plain decimal");
                assertEquals(0, new BigDecimal(expected.get(field).toString()).compareTo(new BigDecimal(value.asText())), field);
            }
        }
    }

    private void freshPersistenceContext() {
        properties.flush();
        entities.clear();
    }

    private Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Decimal roundtrip property");
        body.put("type", "LAND");
        body.put("city", "مكة المكرمة");
        body.put("district", "النوارية");
        body.put("price", "500000.25");
        body.put("area", "150.25");
        body.put("purpose", "RESIDENTIAL");
        body.put("status", "ACTIVE");
        return body;
    }
}
