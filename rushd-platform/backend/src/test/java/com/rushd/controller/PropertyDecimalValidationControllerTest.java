package com.rushd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.Property;
import com.rushd.entity.PropertyPurpose;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.PropertyType;
import com.rushd.entity.Role;
import com.rushd.entity.User;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import com.rushd.service.AuthCookieService;
import com.rushd.service.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PropertyDecimalValidationControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @MockBean private UserRepository users;
    @MockBean private PropertyRepository properties;
    private Cookie adminAccess;
    private User admin;

    @BeforeEach
    void setup() {
        admin = user("decimal-admin@example.com", Role.ADMIN);
        adminAccess = access(admin);
        when(properties.save(any(Property.class))).thenAnswer(invocation -> {
            Property property = invocation.getArgument(0);
            property.setId(91L);
            return property;
        });
        Property existing = new Property();
        existing.setId(91L);
        existing.setPublisher(admin);
        existing.setTitle("Existing property");
        existing.setType(PropertyType.LAND);
        existing.setCity("مكة المكرمة");
        existing.setDistrict("النوارية");
        existing.setArea(new BigDecimal("150"));
        existing.setPrice(new BigDecimal("500000"));
        existing.setPurpose(PropertyPurpose.RESIDENTIAL);
        existing.setStatus(PropertyStatus.ACTIVE);
        when(properties.findById(91L)).thenReturn(Optional.of(existing));
        when(properties.findByIdForUpdate(91L)).thenReturn(Optional.of(existing));
    }

    @ParameterizedTest
    @ValueSource(strings = {"price", "area", "streetWidth", "latitude", "longitude"})
    void invalidPrecisionBoundsAndHugeExponentsReturn400BeforeCreateOrUpdate(String field) throws Exception {
        for (String value : invalidValues(field)) {
            Map<String, Object> body = validBody();
            body.put(field, value);
            String quoted = mapper.writeValueAsString(body);
            for (String json : new String[]{quoted, quoted.replace("\"" + value + "\"", value)}) {
                for (boolean updating : new boolean[]{false, true}) {
                    perform(updating, json).andExpect(status().isBadRequest())
                            .andExpect(jsonPath("$.message." + field).exists());
                }
            }
        }
        verify(properties, never()).save(any(Property.class));
        verify(properties, never()).findById(91L);
        verify(properties, never()).findByIdForUpdate(91L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"price", "area", "streetWidth", "latitude", "longitude"})
    void malformedDecimalValuesReturn400BeforePersistence(String field) throws Exception {
        for (Object malformed : new Object[]{"NaN", "Infinity", "abc", "1,50", true, List.of(1), Map.of("value", 1)}) {
            Map<String, Object> body = validBody();
            body.put(field, malformed);
            for (boolean updating : new boolean[]{false, true}) {
                perform(updating, mapper.writeValueAsString(body)).andExpect(status().isBadRequest());
            }
        }
        verify(properties, never()).save(any(Property.class));
    }

    @Test
    void validScientificValuesReturnPlainStringsWithoutGlobalBigDecimalSerializationChanges() throws Exception {
        Map<String, Object> body = validBody();
        body.put("price", "5E+5");
        body.put("area", "1.5E+2");
        body.put("streetWidth", "2.5E+1");
        body.put("latitude", "-1E-7");
        body.put("longitude", "1.8E+2");
        for (boolean updating : new boolean[]{false, true}) {
            perform(updating, mapper.writeValueAsString(body))
                    .andExpect(updating ? status().isOk() : status().isCreated())
                    .andExpect(jsonPath("$.price").value("500000"))
                    .andExpect(jsonPath("$.area").value("150"))
                    .andExpect(jsonPath("$.streetWidth").value("25"))
                    .andExpect(jsonPath("$.latitude").value("-0.0000001"))
                    .andExpect(jsonPath("$.longitude").value("180"));
        }
        assertTrue(mapper.readTree(mapper.writeValueAsString(Map.of("unrelated", new BigDecimal("1.23"))))
                .get("unrelated").isNumber());
    }

    @Test
    void legacyJsonNumbersAndMissingOptionalValuesRemainValid() throws Exception {
        Map<String, Object> body = validBody();
        body.put("price", new BigDecimal("1250000.50"));
        body.put("area", new BigDecimal("450.50"));
        body.remove("listingType");
        for (boolean updating : new boolean[]{false, true}) {
            perform(updating, mapper.writeValueAsString(body))
                    .andExpect(updating ? status().isOk() : status().isCreated())
                    .andExpect(jsonPath("$.listingType").value("SALE"))
                    .andExpect(jsonPath("$.price").value("1250000.50"))
                    .andExpect(jsonPath("$.area").value("450.50"))
                    .andExpect(jsonPath("$.streetWidth").value(nullValue()))
                    .andExpect(jsonPath("$.latitude").value(nullValue()))
                    .andExpect(jsonPath("$.longitude").value(nullValue()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"price", "area"})
    void requiredDecimalFieldsCannotBeNull(String field) throws Exception {
        Map<String, Object> body = validBody();
        body.put(field, null);
        for (boolean updating : new boolean[]{false, true}) {
            perform(updating, mapper.writeValueAsString(body)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message." + field).exists());
        }
        verify(properties, never()).save(any(Property.class));
    }

    @Test
    void numericContractChangeDoesNotGrantPublishingRightsOrBypassCsrf() throws Exception {
        String body = mapper.writeValueAsString(validBody());
        mvc.perform(post("/api/properties").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        Cookie buyerAccess = access(user("decimal-buyer@example.com", Role.BUYER));
        mvc.perform(post("/api/properties").with(csrf()).cookie(buyerAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/properties/91").with(csrf()).cookie(buyerAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/properties").cookie(adminAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        verify(properties, never()).save(any(Property.class));
    }

    private ResultActions perform(boolean update, String body) throws Exception {
        MockHttpServletRequestBuilder request = update ? put("/api/properties/91") : post("/api/properties");
        return mvc.perform(request.with(csrf()).cookie(adminAccess).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private List<String> invalidValues(String field) {
        List<String> extremes = List.of("1E+2147483647", "0E+2147483647", "1E-2147483647", "0E-2147483647");
        List<String> values = new java.util.ArrayList<>(extremes);
        switch (field) {
            case "price" -> values.addAll(List.of("0", "-1", "100000000000000000", "1.001", "1.000"));
            case "area" -> values.addAll(List.of("0", "-1", "10000000000", "1.001", "1.000"));
            case "streetWidth" -> values.addAll(List.of("0", "-1", "100000000", "1.001", "1.000"));
            case "latitude" -> values.addAll(List.of("90.0000001", "-90.0000001", "0.00000001", "21.10000000"));
            case "longitude" -> values.addAll(List.of("180.0000001", "-180.0000001", "0.00000001", "39.10000000"));
            default -> throw new IllegalArgumentException(field);
        }
        return values;
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Decimal property");
        body.put("type", "LAND");
        body.put("listingType", "SALE");
        body.put("city", "مكة المكرمة");
        body.put("district", "النوارية");
        body.put("area", "150.00");
        body.put("price", "500000.00");
        body.put("purpose", "RESIDENTIAL");
        body.put("status", "ACTIVE");
        return body;
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setId(role == Role.ADMIN ? 1L : 2L);
        user.setName("Decimal test");
        user.setEmail(email);
        user.setPassword("unused-hash");
        user.setRole(role);
        when(users.findByEmail(email)).thenReturn(Optional.of(user));
        return user;
    }

    private Cookie access(User user) {
        return new Cookie(AuthCookieService.ACCESS, jwt.generateAccessToken(user.getEmail()));
    }
}
