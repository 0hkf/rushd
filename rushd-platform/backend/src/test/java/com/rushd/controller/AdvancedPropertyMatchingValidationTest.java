package com.rushd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.*;
import com.rushd.repository.*;
import com.rushd.service.AuthCookieService;
import com.rushd.service.JwtService;
import com.rushd.validation.PropertyMatchingFields;
import com.rushd.validation.PropertySpecificationFields;
import com.rushd.validation.PropertySpecificationsRules;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import java.util.stream.Stream;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc
class AdvancedPropertyMatchingValidationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JwtService jwt;
    @MockBean UserRepository users;
    @MockBean PropertyRepository properties;
    Cookie access;
    @BeforeEach void setup() {
        User user = new User(); user.setId(712L); user.setEmail("p06-validation@example.invalid");
        user.setName("Validation"); user.setPassword("unused"); user.setRole(Role.BUYER);
        when(users.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        access = new Cookie(AuthCookieService.ACCESS, jwt.generateAccessToken(user.getEmail()));
    }
    @AfterEach void beforeQuery() { verifyNoInteractions(properties); }
    static Stream<String> fields() { return PropertyMatchingFields.ALL.keySet().stream(); }
    Map<String, Object> needs(String name) {
        PropertyType type = Arrays.stream(PropertyType.values())
                .filter(t -> PropertySpecificationsRules.allowedFields(t, CommercialScope.UNIT).contains(name)).findFirst().orElse(PropertyType.LAND);
        Map<String, Object> body = new LinkedHashMap<>(Map.of("type", type.name(), "listingType", "SALE", "maxBudget", "99999999999999999.99"));
        if (type == PropertyType.COMMERCIAL) body.put("commercialScope", "UNIT");
        return body;
    }
    void invalid(Map<String, Object> body) throws Exception { invalid(mapper.writeValueAsString(body)); }
    void invalid(String body) throws Exception {
        for (String path : List.of("matches", "validate")) mvc.perform(post("/api/property-needs/" + path)
                .cookie(access).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @MethodSource("fields")
    void everyFieldRejectsBadShapeTypesAndBoundsBeforeQuery(String name) throws Exception {
        var field = PropertyMatchingFields.ALL.get(name);
        Map<String, Object> body = needs(name);
        List<String> invalidValues = field.type() == Integer.class
                ? List.of("\"3\"", "3.0", "3e0", "1e99999", "2147483648", "true", "{}", "[]")
                : field.type() == Boolean.class ? List.of("\"false\"", "0", "1", "null", "{}", "[]")
                : List.of("0", "true", "\"unknown\"", "null", "{}", "[]");
        for (String value : invalidValues) {
            String criterion = field.type() == Integer.class ? "{\"min\":" + value + ",\"requirement\":\"REQUIRED\"}"
                    : "{\"value\":" + value + ",\"requirement\":\"REQUIRED\"}";
            String base = mapper.writeValueAsString(body);
            invalid(base.substring(0, base.length()-1) + ",\"specificationCriteria\":{\"" + name + "\":" + criterion + "}}");
        }
        if (field.type() == Integer.class) {
            for (Map<String, ?> criterion : List.of(
                    Map.of("min", field.min()-1, "requirement", "REQUIRED"),
                    Map.of("max", field.max()+1, "requirement", "PREFERRED"),
                    Map.of("min", field.max(), "max", field.min(), "requirement", "REQUIRED"),
                    Map.of("requirement", "REQUIRED"), Map.of("min", field.min()))) {
                body.put("specificationCriteria", Map.of(name, criterion)); invalid(body);
            }
        } else {
            Object valid = field.type() == Boolean.class ? false : ((Enum<?>) field.type().getEnumConstants()[0]).name();
            body.put("specificationCriteria", Map.of(name, Map.of("value", valid))); invalid(body);
            body.put("specificationCriteria", Map.of(name, Map.of("value", valid, "min", 0, "requirement", "REQUIRED"))); invalid(body);
        }
    }
    @Test void unknownDuplicateNullAndDeferredFieldsReject() throws Exception {
        String base = "{\"type\":\"APARTMENT\",\"listingType\":\"SALE\",\"maxBudget\":\"500000\",";
        for (String group : List.of("null", "[]", "true", "{\"estimatedAgeYears\":{}}", "{\"otherTypeDescription\":{}}", "{\"unitNetArea\":{}}", "{\"bedrooms\":null}", "{\"bedrooms\":{},\"bedrooms\":{}}", "{\"bedrooms\":{\"min\":0,\"min\":1,\"requirement\":\"REQUIRED\"}}", "{\"bedrooms\":{\"min\":0,\"requirement\":0}}", "{\"bedrooms\":{\"min\":0,\"requirement\":\"REQUIRED\",\"injected\":1}}"))
            invalid(base + "\"specificationCriteria\":" + group + "}");
        invalid(base + "\"specificationCriteria\":{},\"specificationCriteria\":{}}");
        invalid(base + "\"areaBasis\":null,\"areaBasis\":null}");
        invalid(base + "\"commercialScope\":null,\"commercialScope\":null}");
    }
    @Test void applicabilityScopeAndAreaBasisAreValidated() throws Exception {
        Map<String, Object> body = needs("landArea");
        body.put("type", "LAND"); body.put("specificationCriteria", Map.of("hasElevator", Map.of("value", false, "requirement", "REQUIRED"))); invalid(body);
        body.remove("specificationCriteria"); body.put("commercialScope", "UNIT"); invalid(body);
        body.remove("commercialScope"); body.put("areaBasis", "UNIT_NET"); body.put("minArea", "1"); invalid(body);
        body.put("type", "APARTMENT"); body.remove("minArea"); invalid(body);
        body.put("areaBasis", 0); invalid(body);
        body.put("type", "COMMERCIAL"); body.put("minArea", "1"); body.put("areaBasis", "UNIT_NET"); invalid(body);
        body.put("commercialScope", "WHOLE_BUILDING"); invalid(body);
        body.put("areaBasis", "ADVERTISED"); body.put("specificationCriteria", Map.of("floorNumber", Map.of("min", 0, "requirement", "REQUIRED"))); invalid(body);
    }
    @ParameterizedTest @MethodSource("fields")
    void acceptedBoundsAndEnumsRemainStrictAndEchoCorrectly(String name) throws Exception {
        var field = PropertyMatchingFields.ALL.get(name);
        Map<String, Object> body = needs(name);
        List<Object> values = field.type() == Integer.class ? List.of(field.min(), field.max())
                : field.type() == Boolean.class ? List.of(true, false) : Arrays.asList(field.type().getEnumConstants());
        for (Object value : values) {
            Map<String, Object> criterion = field.type() == Integer.class
                    ? Map.of("min", value, "max", value, "requirement", "PREFERRED")
                    : Map.of("value", value, "requirement", "REQUIRED");
            body.put("specificationCriteria", Map.of(name, criterion));
            mvc.perform(post("/api/property-needs/validate").cookie(access).with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON)
                            .content(mapper.writeValueAsString(body))).andExpect(status().isOk())
                    .andExpect(jsonPath("$.specificationCriteria." + name + ".requirement").value(criterion.get("requirement")));
        }
    }
}
