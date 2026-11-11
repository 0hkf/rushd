package com.rushd.controller;

import com.fasterxml.jackson.databind.*;
import com.rushd.entity.*;
import com.rushd.repository.*;
import com.rushd.service.*;
import com.rushd.validation.*;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest @AutoConfigureMockMvc @Transactional
class AdvancedPropertyMatchingControllerTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PropertyRepository properties;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired EntityManager entities;
    User admin;
    Cookie access;
    @BeforeEach void setup() {
        admin = user("p06-admin@example.invalid", Role.ADMIN);
        User buyer = user("p06-buyer@example.invalid", Role.BUYER);
        access = new Cookie(AuthCookieService.ACCESS, jwt.generateAccessToken(buyer.getEmail()));
    }
    static Stream<String> fields() { return PropertyMatchingFields.ALL.keySet().stream(); }
    User user(String email, Role role) {
        User user = new User(); user.setEmail(email); user.setName("P06 fixture"); user.setPassword("unused"); user.setRole(role);
        return users.saveAndFlush(user);
    }
    Property unsaved(PropertyType type, PropertySpecifications specs) {
        Property p = new Property(); p.setPublisher(admin); p.setTitle("P06 fixture"); p.setType(type);
        p.setCity("مكة المكرمة"); p.setDistrict("حي الياسمين"); p.setListingType(ListingType.SALE);
        p.setPrice(new BigDecimal("9007199254740993.01")); p.setArea(new BigDecimal("900.00"));
        p.setStatus(PropertyStatus.ACTIVE); p.setPurpose(PropertyPurpose.RESIDENTIAL); p.setSpecifications(specs); return p;
    }
    Map<String, Object> needs(PropertyType type) { return new LinkedHashMap<>(Map.of("type", type.name(), "listingType", "SALE", "maxBudget", "9007199254740993.01")); }
    ResultActions perform(Map<String, Object> body, int page, int size) throws Exception {
        properties.flush(); entities.clear();
        return mvc.perform(post("/api/property-needs/matches").cookie(access).with(csrf().asHeader())
                .param("page", ""+page).param("size", ""+size).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }
    JsonNode result(Map<String, Object> body) throws Exception {
        return mapper.readTree(perform(body, 0, 20).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
    }
    <T> void set(PropertySpecificationFields.Field<T> field, PropertySpecifications specs, Object value) { field.write().accept(specs, field.type().cast(value)); }
    @ParameterizedTest @MethodSource("fields")
    void all28CriteriaFilterRankAndReturnExactEvidence(String name) throws Exception {
        var field = PropertyMatchingFields.ALL.get(name);
        PropertyType type = Arrays.stream(PropertyType.values()).filter(t -> PropertySpecificationsRules.allowedFields(t, CommercialScope.UNIT).contains(name)).findFirst().orElseThrow();
        Object expected = field.type() == Integer.class ? field.min() : field.type() == Boolean.class ? false : field.type().getEnumConstants()[0];
        Object different = field.type() == Integer.class ? field.min()+1 : field.type() == Boolean.class ? true : field.type().getEnumConstants()[1];
        PropertySpecifications known = new PropertySpecifications(), wrong = new PropertySpecifications(), unknown = new PropertySpecifications();
        if (type == PropertyType.COMMERCIAL) { known.setCommercialScope(CommercialScope.UNIT); wrong.setCommercialScope(CommercialScope.UNIT); unknown.setCommercialScope(CommercialScope.UNIT); }
        set(field, known, expected); set(field, wrong, different);
        Property match = properties.saveAndFlush(unsaved(type, known));
        properties.saveAndFlush(unsaved(type, wrong)); properties.saveAndFlush(unsaved(type, unknown));
        var before = match.getUpdatedAt();
        Map<String, Object> body = needs(type);
        if (type == PropertyType.COMMERCIAL) body.put("commercialScope", "UNIT");
        Map<String, Object> criterion = new LinkedHashMap<>(); criterion.put("requirement", "PREFERRED");
        if (field.type() == Integer.class) { criterion.put("min", expected); criterion.put("max", expected); } else criterion.put("value", expected);
        body.put("specificationCriteria", Map.of(name, criterion));
        JsonNode result = result(body);
        assertEquals("matching-v2", result.get("rulesVersion").asText());
        assertEquals(3, result.get("totalElements").asInt());
        JsonNode first = result.get("content").get(0);
        assertEquals(match.getId().longValue(), first.get("id").asLong());
        assertEquals("9007199254740993.01", first.get("price").asText());
        assertEquals(1, first.get("preferredCount").asInt()); assertEquals(1, first.get("preferredMatchedCount").asInt());
        assertEquals(expected instanceof Enum<?> e ? e.name() : expected.toString(), first.get("specificationMatches").get(0).get("actual").asText());
        Set<String> outcomes = new HashSet<>(); result.get("content").forEach(row -> outcomes.add(row.get("specificationMatches").get(0).get("outcome").asText()));
        assertEquals(Set.of("MATCHED", "MISMATCHED", "UNKNOWN"), outcomes);
        criterion.put("requirement", "REQUIRED");
        JsonNode required = result(body); assertEquals(1, required.get("totalElements").asInt());
        assertEquals(0, required.get("content").get(0).get("preferredCount").asInt());
        assertEquals(before, entities.find(Property.class, match.getId()).getUpdatedAt());
    }
    @Test void namedAreaNeverFallsBackToAdvertisedAndOldRequestStillUsesV1() throws Exception {
        PropertySpecifications specs = new PropertySpecifications(); specs.setUnitNetArea(new BigDecimal("150.25"));
        Property named = properties.saveAndFlush(unsaved(PropertyType.APARTMENT, specs));
        properties.saveAndFlush(unsaved(PropertyType.APARTMENT, null));
        Map<String, Object> body = needs(PropertyType.APARTMENT); body.put("minArea", "150.25"); body.put("maxArea", "150.25"); body.put("areaBasis", "UNIT_NET"); body.put("areaRequirement", "PREFERRED");
        JsonNode result = result(body); assertEquals(2, result.get("totalElements").asInt());
        assertEquals(named.getId().longValue(), result.get("content").get(0).get("id").asLong());
        assertEquals("900.00", result.get("content").get(0).get("area").asText());
        assertEquals("150.25", result.get("content").get(0).get("matchedArea").asText());
        assertTrue(result.get("content").get(1).get("matchedArea").isNull());
        body.put("areaRequirement", "REQUIRED"); assertEquals(1, result(body).get("totalElements").asInt());
        body.remove("areaBasis"); body.put("minArea", "900"); body.put("maxArea", "900.00");
        JsonNode legacy = result(body); assertEquals(2, legacy.get("totalElements").asInt()); assertEquals("matching-v1", legacy.get("rulesVersion").asText());
    }
    @Test void commercialScopeIsAnUnweightedStructuralConditionAndPrivateListingsNeverMatch() throws Exception {
        for (CommercialScope scope : CommercialScope.values()) for (PropertyStatus status : PropertyStatus.values()) {
            PropertySpecifications specs = new PropertySpecifications(); specs.setCommercialScope(scope);
            Property property = unsaved(PropertyType.COMMERCIAL, specs); property.setStatus(status); properties.saveAndFlush(property);
        }
        properties.saveAndFlush(unsaved(PropertyType.COMMERCIAL, null));
        Map<String, Object> body = needs(PropertyType.COMMERCIAL); body.put("commercialScope", "UNIT");
        JsonNode result = result(body); assertEquals(1, result.get("totalElements").asInt()); assertEquals(0, result.get("content").get(0).get("preferredCount").asInt());
    }
    @Test void requiredSpecReducesCandidatesBeforeLimitButPreferenceDoesNotHideAnOversizedSet() throws Exception {
        List<Property> rows = new ArrayList<>();
        for (int i=0;i<1001;i++) { PropertySpecifications specs = new PropertySpecifications(); specs.setBedrooms(i==1000 ? 3 : 0); rows.add(unsaved(PropertyType.APARTMENT, specs)); }
        properties.saveAll(rows);
        Map<String, Object> body = needs(PropertyType.APARTMENT);
        body.put("specificationCriteria", Map.of("bedrooms", Map.of("min", 3, "requirement", "REQUIRED")));
        assertEquals(1, result(body).get("totalElements").asInt());
        body.put("specificationCriteria", Map.of("bedrooms", Map.of("min", 3, "requirement", "PREFERRED")));
        perform(body, 0, 10).andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.content").doesNotExist());
    }
    @Test void rankingIsGlobalAcrossPagesAndEachRangeCountsOnceWithOldPreferences() throws Exception {
        PropertySpecifications a = new PropertySpecifications(); a.setBedrooms(3); a.setHasElevator(false);
        PropertySpecifications b = new PropertySpecifications(); b.setBedrooms(4); b.setHasElevator(true);
        Property first = properties.saveAndFlush(unsaved(PropertyType.APARTMENT, a));
        properties.saveAndFlush(unsaved(PropertyType.APARTMENT, b));
        Map<String, Object> body = needs(PropertyType.APARTMENT); body.put("district", "الياسمين"); body.put("districtRequirement", "PREFERRED");
        body.put("minArea", "899.99"); body.put("maxArea", "900.01"); body.put("areaRequirement", "PREFERRED");
        body.put("specificationCriteria", Map.of("bedrooms", Map.of("min", 3, "max", 4, "requirement", "PREFERRED"), "hasElevator", Map.of("value", false, "requirement", "PREFERRED")));
        JsonNode page0 = mapper.readTree(perform(body,0,1).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertEquals(first.getId().longValue(), page0.get("content").get(0).get("id").asLong());
        assertEquals(4, page0.get("content").get(0).get("preferredMatchedCount").asInt());
        assertEquals(4, page0.get("content").get(0).get("preferredCount").asInt());
        JsonNode page1 = mapper.readTree(perform(body,1,1).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray());
        assertNotEquals(first.getId().longValue(), page1.get("content").get(0).get("id").asLong());
    }
    @ParameterizedTest @EnumSource(PropertyType.class)
    void allSevenTypesMatchOnlyTheirOwnKnownZeroIncludingOther(PropertyType type) throws Exception {
        PropertySpecifications specs = new PropertySpecifications(); specs.setParkingSpaces(0);
        Property known = properties.saveAndFlush(unsaved(type, specs));
        properties.saveAndFlush(unsaved(type, null));
        Map<String, Object> body = needs(type);
        body.put("specificationCriteria", Map.of("parkingSpaces", Map.of("min", 0, "max", 0, "requirement", "REQUIRED")));
        JsonNode result = result(body);
        assertEquals(1, result.get("totalElements").asInt());
        assertEquals(known.getId().longValue(), result.get("content").get(0).get("id").asLong());
    }
    @ParameterizedTest @ValueSource(strings={"LAND", "UNIT_NET", "UNIT_GROSS", "TOTAL_BUILT"})
    void eachNamedMeasurementQueriesItsOwnColumnAtPreciseBounds(String basis) throws Exception {
        var measurement = com.rushd.dto.PropertyNeedsAreaBasis.valueOf(basis);
        var field = PropertySpecificationFields.BY_NAME.get(measurement.field());
        for (String bound : List.of("0.01", "9999999999.99")) {
            PropertySpecifications specs = new PropertySpecifications(); set(field, specs, new BigDecimal(bound));
            Property known = properties.saveAndFlush(unsaved(PropertyType.OTHER, specs));
            Map<String, Object> body = needs(PropertyType.OTHER);
            body.put("areaBasis", basis); body.put("minArea", bound); body.put("maxArea", bound);
            JsonNode result = result(body); assertEquals(1, result.get("totalElements").asInt());
            assertEquals(known.getId().longValue(), result.get("content").get(0).get("id").asLong());
            assertEquals(bound, result.get("content").get(0).get("matchedArea").asText());
        }
    }
    @Test void equalPreferencesUseNewestThenIdAndNewCriteriaStillRequireAuthenticationAndCsrf() throws Exception {
        PropertySpecifications specs = new PropertySpecifications(); specs.setHasElevator(false);
        Property old = properties.saveAndFlush(unsaved(PropertyType.APARTMENT, specs));
        Property newer = properties.saveAndFlush(unsaved(PropertyType.APARTMENT, specs.copy()));
        java.time.LocalDateTime same = java.time.LocalDateTime.of(2026,1,1,0,0);
        org.springframework.test.util.ReflectionTestUtils.setField(old, "createdAt", same);
        org.springframework.test.util.ReflectionTestUtils.setField(newer, "createdAt", same);
        Map<String, Object> body = needs(PropertyType.APARTMENT); body.put("specificationCriteria", Map.of("hasElevator", Map.of("value", false, "requirement", "PREFERRED")));
        assertEquals(newer.getId().longValue(), result(body).get("content").get(0).get("id").asLong());
        mvc.perform(post("/api/property-needs/matches").with(csrf().asHeader()).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/property-needs/matches").cookie(access).contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body))).andExpect(status().isForbidden());
    }
}
