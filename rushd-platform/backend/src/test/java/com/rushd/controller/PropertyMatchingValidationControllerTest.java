package com.rushd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.Role;
import com.rushd.entity.User;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import com.rushd.service.AuthCookieService;
import com.rushd.service.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PropertyMatchingValidationControllerTest {
    private static final String ENDPOINT = "/api/property-needs/matches";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @MockBean private UserRepository users;
    @MockBean private PropertyRepository properties;
    private Cookie access;

    @BeforeEach
    void setup() {
        User user = new User();
        user.setId(23L);
        user.setEmail("matching-validation@example.com");
        user.setName("Matching validation");
        user.setPassword("unused-hash");
        user.setRole(Role.BUYER);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        access = new Cookie(AuthCookieService.ACCESS, jwt.generateAccessToken(user.getEmail()));
    }

    @AfterEach
    void invalidRequestsNeverReachPropertyQuery() {
        verifyNoInteractions(properties);
    }

    @Test
    void anonymousWithValidCsrfReceives401() throws Exception {
        mvc.perform(post(ENDPOINT).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(needs())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void csrfCannotBeOmittedOrForged() throws Exception {
        mvc.perform(post(ENDPOINT).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(needs())))
                .andExpect(status().isForbidden());
        mvc.perform(post(ENDPOINT).with(csrf().asHeader().useInvalidToken()).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(needs())))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @ValueSource(strings = {"type", "listingType", "maxBudget"})
    void commonRequiredFieldsAreValidatedBeforeAnyQuery(String field) throws Exception {
        Map<String, Object> body = needs();
        body.remove(field);
        mvc.perform(post(ENDPOINT).with(csrf().asHeader()).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message." + field).exists());
    }

    @ParameterizedTest
    @CsvSource({"type, 0", "listingType, 0", "rentalPeriod, 0", "areaRequirement, 0", "districtRequirement, 0", "searchGoal, 0", "maxBudget, 1E+2147483647", "minArea, 1E+2147483647", "maxArea, 1E+2147483647"})
    void r1StrictEnumsAndNumericBoundsRemainApplied(String field, String value) throws Exception {
        Map<String, Object> body = needs();
        body.put(field, value.equals("0") ? 0 : value);
        mvc.perform(post(ENDPOINT).with(csrf().asHeader()).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @CsvSource({"page, -1", "page, 2147483648", "size, 0", "size, 21", "size, abc", "sort, price", "city, Makkah"})
    void queryBoundsAndUnsupportedParametersAreRejected(String field, String value) throws Exception {
        mvc.perform(post(ENDPOINT).with(csrf().asHeader()).cookie(access).param(field, value)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(needs())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void repeatedPageParametersAreRejectedRatherThanAmbiguous() throws Exception {
        mvc.perform(post(ENDPOINT).with(csrf().asHeader()).cookie(access).param("page", "0", "1")
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(needs())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void unsupportedBodyCriteriaAndInvertedRangeAreRejectedBeforeQuery() throws Exception {
        Map<String, Object> body = needs();
        body.put("bedrooms", 3);
        mvc.perform(post(ENDPOINT).with(csrf().asHeader()).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest());
        body.remove("bedrooms");
        body.put("minArea", "200");
        body.put("maxArea", "100");
        mvc.perform(post(ENDPOINT).with(csrf().asHeader()).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message.maxArea").exists());
    }

    private Map<String, Object> needs() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "LAND");
        body.put("listingType", "SALE");
        body.put("maxBudget", "500000");
        return body;
    }
}
