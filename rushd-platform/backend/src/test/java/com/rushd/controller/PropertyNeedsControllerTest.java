package com.rushd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.PropertyType;
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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PropertyNeedsControllerTest {
    private static final String ENDPOINT = "/api/property-needs/validate";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @MockBean private UserRepository userRepository;
    @MockBean private PropertyRepository propertyRepository;
    private Cookie access;

    @BeforeEach
    void setUp() {
        User user = new User();
        user.setId(41L);
        user.setName("Needs user");
        user.setEmail("needs@example.com");
        user.setPassword("unused-hash");
        user.setRole(Role.BUYER);
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        access = new Cookie(AuthCookieService.ACCESS, jwt.generateAccessToken(user.getEmail()));
    }

    @AfterEach
    void inputValidationNeverReadsOrSavesPropertiesOrSavesUsers() {
        verifyNoInteractions(propertyRepository);
        verify(userRepository, never()).save(any(User.class));
    }

    @ParameterizedTest
    @EnumSource(PropertyType.class)
    void supportsAllSevenTypesWithoutReturningRecommendations(PropertyType type) throws Exception {
        Map<String, Object> body = validBody();
        body.put("type", type.name());
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.type").value(type.name()))
                .andExpect(jsonPath("$.listingType").value("SALE"))
                .andExpect(jsonPath("$.maxBudget").value("500000.00"))
                .andExpect(jsonPath("$.city").value("مكة المكرمة"))
                .andExpect(jsonPath("$.currency").value("SAR"))
                .andExpect(jsonPath("$.areaUnit").value("SQUARE_METRE"))
                .andExpect(jsonPath("$.areaRequirement").value(nullValue()))
                .andExpect(jsonPath("$.districtRequirement").value(nullValue()))
                .andExpect(jsonPath("$.results").doesNotExist())
                .andExpect(jsonPath("$.score").doesNotExist())
                .andExpect(jsonPath("$.id").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"MONTHLY", "YEARLY"})
    void acceptsBothRentalPeriods(String period) throws Exception {
        Map<String, Object> body = validBody();
        body.put("listingType", "RENT");
        body.put("rentalPeriod", period);
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.rentalPeriod").value(period));
    }

    @ParameterizedTest
    @CsvSource({"RENT, NULL", "SALE, MONTHLY", "SALE, YEARLY"})
    void rejectsInconsistentRentalPeriod(String listing, String period) throws Exception {
        Map<String, Object> body = validBody();
        body.put("listingType", listing);
        body.put("rentalPeriod", period.equals("NULL") ? null : period);
        perform(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.rentalPeriod").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"listingType", "type", "maxBudget"})
    void requiresExplicitCommonFields(String field) throws Exception {
        Map<String, Object> body = validBody();
        body.remove(field);
        perform(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message." + field).exists());
        body.put(field, null);
        perform(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message." + field).exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "100000000000000000", "1.001"})
    void rejectsInvalidBudget(String budget) throws Exception {
        Map<String, Object> body = validBody();
        body.put("maxBudget", budget);
        perform(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.maxBudget").exists());
    }

    @Test
    void preservesFullDecimalPrecisionAtSupportedLimits() throws Exception {
        Map<String, Object> body = validBody();
        body.put("maxBudget", "99999999999999999.99");
        body.put("minArea", "0.01");
        body.put("maxArea", "9999999999.99");
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxBudget").value("99999999999999999.99"))
                .andExpect(jsonPath("$.minArea").value("0.01"))
                .andExpect(jsonPath("$.maxArea").value("9999999999.99"))
                .andExpect(jsonPath("$.areaRequirement").value("REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"maxBudget", "minArea", "maxArea"})
    void extremeScientificValuesCannotBypassBoundsOrReachResponseSerialization(String field) throws Exception {
        for (String extreme : new String[]{"1E+2147483647", "1E+2147483646", "1E-2147483647"}) {
            Map<String, Object> body = validBody();
            body.put(field, extreme);
            String quotedJson = mapper.writeValueAsString(body);
            for (String json : new String[]{quotedJson, quotedJson.replace("\"" + extreme + "\"", extreme)}) {
                mvc.perform(post(ENDPOINT).with(csrf()).cookie(access).contentType(MediaType.APPLICATION_JSON)
                                .content(json))
                        .andExpect(status().isBadRequest())
                        .andExpect(jsonPath("$.message." + field).exists());
            }
        }
    }

    @Test
    void acceptsMinimumRepresentablePositiveBudgetAndArea() throws Exception {
        Map<String, Object> body = validBody();
        body.put("maxBudget", "0.01");
        body.put("minArea", "0.01");
        body.put("maxArea", "0.01");
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxBudget").value("0.01"))
                .andExpect(jsonPath("$.minArea").value("0.01"))
                .andExpect(jsonPath("$.maxArea").value("0.01"));
    }

    @ParameterizedTest
    @CsvSource({"minArea, 0", "maxArea, -1", "minArea, 10000000000", "maxArea, 1.001"})
    void rejectsInvalidArea(String field, String value) throws Exception {
        Map<String, Object> body = validBody();
        body.put(field, value);
        perform(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message." + field).exists());
    }

    @Test
    void validatesAreaOrderingAndAcceptsInclusiveEqualBounds() throws Exception {
        Map<String, Object> body = validBody();
        body.put("minArea", "200.00");
        body.put("maxArea", "199.99");
        perform(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.maxArea").exists());
        body.put("maxArea", "200.00");
        body.put("areaRequirement", "PREFERRED");
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.minArea").value("200.00"))
                .andExpect(jsonPath("$.maxArea").value("200.00"))
                .andExpect(jsonPath("$.areaRequirement").value("PREFERRED"));
    }

    @Test
    void trimsDistrictAndAppliesCriterionDefaults() throws Exception {
        Map<String, Object> body = validBody();
        body.put("district", "  النوارية  ");
        body.put("minArea", "150");
        body.put("searchGoal", "INVESTMENT");
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.district").value("النوارية"))
                .andExpect(jsonPath("$.districtRequirement").value("PREFERRED"))
                .andExpect(jsonPath("$.areaRequirement").value("REQUIRED"))
                .andExpect(jsonPath("$.searchGoal").value("INVESTMENT"));
        body.put("districtRequirement", "REQUIRED");
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.districtRequirement").value("REQUIRED"));
    }

    @Test
    void normalizesNullOrBlankOptionalInputsWithoutInventingRequirements() throws Exception {
        Map<String, Object> body = validBody();
        body.put("district", "   ");
        body.put("districtRequirement", null);
        body.put("minArea", null);
        body.put("maxArea", null);
        body.put("areaRequirement", null);
        body.put("rentalPeriod", null);
        body.put("searchGoal", null);
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.district").value(nullValue()))
                .andExpect(jsonPath("$.districtRequirement").value(nullValue()))
                .andExpect(jsonPath("$.minArea").value(nullValue()))
                .andExpect(jsonPath("$.maxArea").value(nullValue()))
                .andExpect(jsonPath("$.areaRequirement").value(nullValue()))
                .andExpect(jsonPath("$.searchGoal").value(nullValue()));
        body.put("district", "العزيزية");
        body.put("maxArea", "200");
        body.put("areaRequirement", null);
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.districtRequirement").value("PREFERRED"))
                .andExpect(jsonPath("$.areaRequirement").value("REQUIRED"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "NULL"})
    void requiredDistrictCannotBeMissingOrBlank(String district) throws Exception {
        Map<String, Object> body = validBody();
        body.put("districtRequirement", "REQUIRED");
        body.put("district", district.equals("NULL") ? null : district);
        perform(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.district").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"REQUIRED", "PREFERRED"})
    void explicitModesRequireCorrespondingCriteria(String mode) throws Exception {
        Map<String, Object> body = validBody();
        body.put("districtRequirement", mode);
        perform(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.district").exists());
        body.remove("districtRequirement");
        body.put("areaRequirement", mode);
        perform(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.areaRequirement").exists());
    }

    @Test
    void decimalScientificNotationIsNormalizedToPlainStrings() throws Exception {
        Map<String, Object> body = validBody();
        body.put("maxBudget", "5E+5");
        body.put("minArea", "1.5E+2");
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxBudget").value("500000"))
                .andExpect(jsonPath("$.minArea").value("150"));
    }

    @Test
    void districtLengthAppliesAfterTrimming() throws Exception {
        Map<String, Object> body = validBody();
        body.put("district", "  " + "x".repeat(100) + "  ");
        perform(body).andExpect(status().isOk());
        body.put("district", "x".repeat(101));
        perform(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.district").exists());
    }

    @ParameterizedTest
    @CsvSource({"type, HOUSE", "listingType, LEASE", "rentalPeriod, WEEKLY", "districtRequirement, OPTIONAL", "areaRequirement, IGNORE", "searchGoal, MIXED"})
    void rejectsUnsupportedEnumValues(String field, String value) throws Exception {
        Map<String, Object> body = validBody();
        body.put(field, value);
        perform(body).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(strings = {"listingType", "type", "rentalPeriod", "districtRequirement", "areaRequirement", "searchGoal"})
    void enumFieldsRejectNumericOrdinalsRatherThanCoercingToNames(String field) throws Exception {
        Map<String, Object> body = validBody();
        body.put("listingType", "RENT");
        body.put("rentalPeriod", "MONTHLY");
        body.put("district", "العزيزية");
        body.put("minArea", "150");
        for (Object value : new Object[]{0, 1, "0", "1", 0.0, true}) {
            body.put(field, value);
            perform(body).andExpect(status().isBadRequest());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"bedrooms", "purpose", "score", "rentalPeriodValid"})
    void rejectsUnknownFieldsInsteadOfSilentlyAcceptingUnsupportedCriteria(String field) throws Exception {
        Map<String, Object> body = validBody();
        body.put(field, "unsupported");
        perform(body).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void optionalCityIsRestrictedToCanonicalMakkah() throws Exception {
        Map<String, Object> body = validBody();
        body.put("city", "مكة المكرمة");
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.city").value("مكة المكرمة"));
        body.put("city", null);
        perform(body).andExpect(status().isOk());
        for (String city : new String[]{"", "   ", "الرياض", "مكة", " مكة المكرمة "}) {
            body.put("city", city);
            perform(body).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message.city").exists());
        }
    }

    @Test
    void acceptsJsonNumbersButReturnsDecimalStrings() throws Exception {
        Map<String, Object> body = validBody();
        body.put("maxBudget", 500000);
        body.put("minArea", 150);
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.maxBudget").value("500000"))
                .andExpect(jsonPath("$.minArea").value("150"));
    }

    @Test
    void authenticatedRequestIsRequired() throws Exception {
        mvc.perform(post(ENDPOINT).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(validBody())))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void existingCsrfProtectionRejectsMissingOrInvalidToken() throws Exception {
        mvc.perform(post(ENDPOINT).cookie(access).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(validBody())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("Invalid CSRF token"));
        mvc.perform(post(ENDPOINT).with(csrf().useInvalidToken()).cookie(access)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(validBody())))
                .andExpect(status().isForbidden());
    }

    private ResultActions perform(Map<String, Object> body) throws Exception {
        return mvc.perform(post(ENDPOINT).with(csrf()).cookie(access).contentType(MediaType.APPLICATION_JSON)
                .content(mapper.writeValueAsString(body)));
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("listingType", "SALE");
        body.put("type", "LAND");
        body.put("maxBudget", "500000.00");
        return body;
    }
}
