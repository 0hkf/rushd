package com.rushd.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.ListingType;
import com.rushd.entity.Property;
import com.rushd.entity.PropertyPurpose;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.PropertyType;
import com.rushd.entity.RentalPeriod;
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
import org.junit.jupiter.params.provider.CsvSource;
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
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyListingStatusRoundTripTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private PropertyRepository properties;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private EntityManager entities;
    private User admin;
    private Cookie adminAccess;

    @BeforeEach
    void setup() {
        admin = new User();
        admin.setName("Listing status roundtrip");
        admin.setEmail("listing-status-roundtrip@example.com");
        admin.setPassword("unused-hash");
        admin.setRole(Role.ADMIN);
        users.saveAndFlush(admin);
        adminAccess = new Cookie(AuthCookieService.ACCESS, jwt.generateAccessToken(admin.getEmail()));
    }

    @ParameterizedTest
    @CsvSource({
            "SALE,,DRAFT", "SALE,,ACTIVE", "SALE,,INACTIVE", "SALE,,SOLD",
            "RENT,MONTHLY,DRAFT", "RENT,MONTHLY,ACTIVE", "RENT,MONTHLY,INACTIVE",
            "RENT,YEARLY,DRAFT", "RENT,YEARLY,ACTIVE", "RENT,YEARLY,INACTIVE"
    })
    void validListingStatusMatrixCanBeCreatedUpdatedAndReadExactly(
            String listingType, String period, String propertyStatus) throws Exception {
        Map<String, Object> body = body(listingType, period, propertyStatus);
        long id = create(body).get("id").asLong();
        freshPersistenceContext();
        assertStoredState(id, listingType, period, propertyStatus);

        Map<String, Object> initial = body("SALE", null, "ACTIVE");
        long updateId = create(initial).get("id").asLong();
        mvc.perform(put("/api/properties/{id}", updateId).with(csrf()).cookie(adminAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(propertyStatus))
                .andExpect(jsonPath("$.listingType").value(listingType));
        freshPersistenceContext();
        assertStoredState(updateId, listingType, period, propertyStatus);

        if (propertyStatus.equals("SOLD")) {
            mvc.perform(get("/api/properties/{id}", id))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("SOLD"));
        }
    }

    @ParameterizedTest
    @CsvSource({
            "SALE,,false", "SALE,,true", "RENT,MONTHLY,false", "RENT,MONTHLY,true",
            "RENT,YEARLY,false", "RENT,YEARLY,true"
    })
    void createMissingOrNullStatusStillDefaultsToActive(
            String listingType, String period, boolean explicitNull) throws Exception {
        Map<String, Object> body = body(listingType, period, "ACTIVE");
        if (explicitNull) body.put("status", null);
        else body.remove("status");
        long id = create(body).get("id").asLong();
        freshPersistenceContext();
        assertStoredState(id, listingType, period, "ACTIVE");
    }

    @Test
    void legacyPayloadWithoutListingTypeStillCreatesASaleWithDefaultActiveStatus() throws Exception {
        Map<String, Object> body = body("SALE", null, "ACTIVE");
        body.remove("listingType");
        body.remove("status");
        long id = create(body).get("id").asLong();
        freshPersistenceContext();
        assertStoredState(id, "SALE", null, "ACTIVE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MONTHLY", "YEARLY"})
    void invalidUpdateDoesNotChangePersistedDataOrUpdatedAt(String period) throws Exception {
        Map<String, Object> original = body("SALE", null, "SOLD");
        long id = create(original).get("id").asLong();
        freshPersistenceContext();
        Property before = properties.findById(id).orElseThrow();
        LocalDateTime updatedAt = before.getUpdatedAt();
        long publisherId = before.getPublisher().getId();
        long count = properties.count();
        Map<String, Object> attempted = body("RENT", period, "SOLD");
        attempted.put("title", "Should never be saved");
        attempted.put("price", "1.00");
        attempted.put("area", "1.00");
        attempted.put("latitude", "-10.0000000");
        attempted.put("googlePlaceId", "should-not-be-saved");
        mvc.perform(put("/api/properties/{id}", id).with(csrf()).cookie(adminAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(attempted)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.listingStatusValid").exists());
        freshPersistenceContext();
        Property after = properties.findById(id).orElseThrow();
        assertEquals(updatedAt, after.getUpdatedAt());
        assertEquals(publisherId, after.getPublisher().getId());
        assertEquals(count, properties.count());
        assertEquals(original.get("title"), after.getTitle());
        assertEquals(original.get("googlePlaceId"), after.getGooglePlaceId());
        assertEquals(0, new BigDecimal(original.get("latitude").toString()).compareTo(after.getLatitude()));
        assertStoredState(id, "SALE", null, "SOLD");
    }

    @ParameterizedTest
    @ValueSource(strings = {"MONTHLY", "YEARLY"})
    void legacySoldRentalIsReadableUnchangedUntilExplicitlyCorrected(String period) throws Exception {
        Property legacy = new Property();
        legacy.setPublisher(admin);
        legacy.setTitle("Legacy sold rental");
        legacy.setType(PropertyType.APARTMENT);
        legacy.setCity("مكة المكرمة");
        legacy.setDistrict("النوارية");
        legacy.setPrice(new BigDecimal("99999999999999999.99"));
        legacy.setArea(new BigDecimal("150.25"));
        legacy.setPurpose(PropertyPurpose.RESIDENTIAL);
        legacy.setListingType(ListingType.RENT);
        legacy.setRentalPeriod(RentalPeriod.valueOf(period));
        legacy.setStatus(PropertyStatus.SOLD);
        properties.saveAndFlush(legacy);
        long id = legacy.getId();
        freshPersistenceContext();
        LocalDateTime updatedAt = properties.findById(id).orElseThrow().getUpdatedAt();

        mvc.perform(get("/api/properties/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listingType").value("RENT"))
                .andExpect(jsonPath("$.rentalPeriod").value(period))
                .andExpect(jsonPath("$.status").value("SOLD"));
        mvc.perform(get("/api/admin/properties").cookie(adminAccess))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].status").value("SOLD"));
        mvc.perform(get("/api/properties"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        freshPersistenceContext();
        assertEquals(updatedAt, properties.findById(id).orElseThrow().getUpdatedAt());
        assertEquals(PropertyStatus.SOLD, properties.findById(id).orElseThrow().getStatus());

        mvc.perform(put("/api/properties/{id}", id).with(csrf()).cookie(adminAccess)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body("RENT", period, "DRAFT"))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("DRAFT"));
        freshPersistenceContext();
        assertStoredState(id, "RENT", period, "DRAFT");
        mvc.perform(get("/api/properties/{id}", id)).andExpect(status().isNotFound());
    }

    private JsonNode create(Map<String, Object> body) throws Exception {
        return mapper.readTree(mvc.perform(post("/api/properties").with(csrf()).cookie(adminAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private void assertStoredState(long id, String listingType, String period, String propertyStatus) throws Exception {
        Property stored = properties.findById(id).orElseThrow();
        assertEquals(ListingType.valueOf(listingType), stored.getListingType());
        assertEquals(period == null ? null : RentalPeriod.valueOf(period), stored.getRentalPeriod());
        assertEquals(PropertyStatus.valueOf(propertyStatus), stored.getStatus());
        assertEquals(0, new BigDecimal("99999999999999999.99").compareTo(stored.getPrice()));
        assertEquals(0, new BigDecimal("150.25").compareTo(stored.getArea()));
        mvc.perform(get("/api/properties/{id}", id).cookie(adminAccess))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(propertyStatus))
                .andExpect(jsonPath("$.price").value("99999999999999999.99"))
                .andExpect(jsonPath("$.area").value("150.25"));
    }

    private void freshPersistenceContext() {
        properties.flush();
        entities.clear();
    }

    private Map<String, Object> body(String listingType, String period, String propertyStatus) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Listing status property");
        body.put("type", "APARTMENT");
        body.put("listingType", listingType);
        body.put("rentalPeriod", period);
        body.put("status", propertyStatus);
        body.put("city", "مكة المكرمة");
        body.put("district", "النوارية");
        body.put("price", "99999999999999999.99");
        body.put("area", "150.25");
        body.put("latitude", "21.5101000");
        body.put("googlePlaceId", "structured-place-id");
        body.put("purpose", "RESIDENTIAL");
        return body;
    }
}
