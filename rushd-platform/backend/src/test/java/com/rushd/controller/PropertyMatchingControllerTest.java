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
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyMatchingControllerTest {
    private static final String ENDPOINT = "/api/property-needs/matches";
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private PropertyRepository properties;
    @Autowired private UserRepository users;
    @Autowired private JwtService jwt;
    @Autowired private EntityManager entities;
    private User admin;
    private Cookie buyerAccess;
    private Cookie adminAccess;
    private Cookie legacySellerAccess;

    @BeforeEach
    void setup() {
        admin = user("matching-admin@example.com", Role.ADMIN);
        User buyer = user("matching-buyer@example.com", Role.BUYER);
        buyerAccess = access(buyer);
        adminAccess = access(admin);
        legacySellerAccess = access(user("matching-legacy-seller@example.com", Role.SELLER));
    }

    @ParameterizedTest
    @EnumSource(PropertyType.class)
    void matchesEachPropertyTypeAndReturnsOnlyPublicFacts(PropertyType type) throws Exception {
        Property property = property(type, ListingType.SALE, null, "مكة المكرمة", "النوارية", "150", "500000");
        Map<String, Object> body = needs();
        body.put("type", type.name());
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(property.getId()))
                .andExpect(jsonPath("$.content[0].type").value(type.name()))
                .andExpect(jsonPath("$.content[0].preferredMatchedCount").value(0))
                .andExpect(jsonPath("$.content[0].preferredCount").value(0))
                .andExpect(jsonPath("$.content[0].districtMatch").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.content[0].areaMatch").value("NOT_REQUESTED"))
                .andExpect(jsonPath("$.content[0].price").isString())
                .andExpect(jsonPath("$.content[0].area").isString())
                .andExpect(jsonPath("$.content[0].reasons").isArray())
                .andExpect(jsonPath("$.content[0].publisher").doesNotExist())
                .andExpect(jsonPath("$.content[0].publisherId").doesNotExist())
                .andExpect(jsonPath("$.content[0].description").doesNotExist())
                .andExpect(jsonPath("$.content[0].score").doesNotExist())
                .andExpect(jsonPath("$.content[0].rating").doesNotExist())
                .andExpect(jsonPath("$.needs.city").value("مكة المكرمة"))
                .andExpect(jsonPath("$.needs.maxBudget").value("500000.00"))
                .andExpect(jsonPath("$.rulesVersion").value("matching-v1"))
                .andExpect(jsonPath("$.sort").value("PREFERENCES_THEN_NEWEST"));
    }

    @Test
    void coreEligibilityNeverRelaxesTypeListingRentalPeriodPriceOrPublicationEvenForAdmin() throws Exception {
        Property wanted = property(PropertyType.APARTMENT, ListingType.RENT, RentalPeriod.MONTHLY, "مكة", "النوارية", "150", "4500");
        property(PropertyType.APARTMENT, ListingType.RENT, RentalPeriod.YEARLY, "مكة", "النوارية", "150", "4500");
        property(PropertyType.APARTMENT, ListingType.SALE, null, "مكة", "النوارية", "150", "4500");
        property(PropertyType.LAND, ListingType.RENT, RentalPeriod.MONTHLY, "مكة", "النوارية", "150", "4500");
        property(PropertyType.APARTMENT, ListingType.RENT, RentalPeriod.MONTHLY, "مكة", "النوارية", "150", "4500.01");
        property(PropertyType.APARTMENT, ListingType.RENT, RentalPeriod.MONTHLY, "مكة", "النوارية", "150", "0");
        property(PropertyType.APARTMENT, ListingType.RENT, RentalPeriod.MONTHLY, "مكة", "النوارية", "150", "-1");
        for (PropertyStatus state : new PropertyStatus[]{PropertyStatus.DRAFT, PropertyStatus.INACTIVE, PropertyStatus.SOLD}) {
            Property hidden = property(PropertyType.APARTMENT, ListingType.RENT, RentalPeriod.MONTHLY, "مكة", "النوارية", "150", "4500");
            hidden.setStatus(state);
        }
        properties.flush();
        Map<String, Object> body = needs();
        body.put("type", "APARTMENT");
        body.put("listingType", "RENT");
        body.put("rentalPeriod", "MONTHLY");
        body.put("maxBudget", "4500");
        for (Cookie authenticated : new Cookie[]{buyerAccess, adminAccess, legacySellerAccess}) {
            perform(body, authenticated, 0, 10).andExpect(status().isOk())
                    .andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].id").value(wanted.getId()));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1"})
    void nonPositiveLegacyAreaIsUnknownAndNeverExposedAsZero(String area) throws Exception {
        // The schema permits these legacy values, but not SALE with a rental period.
        Property zero = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", area, "100");
        perform(needs()).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(zero.getId()))
                .andExpect(jsonPath("$.content[0].area").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[0].areaMatch").value("NOT_REQUESTED"));
    }

    @Test
    void cityAliasesAreExplicitAndNoLocationFallbackIsAllowed() throws Exception {
        for (String city : new String[]{"مكة", "مكة المكرمة", "  مكة\u00a0\t المكرمة  ", "MAKKAH", "  MaKkAh "}) {
            property(PropertyType.LAND, ListingType.SALE, null, city, "النوارية", "150", "100");
        }
        for (String city : new String[]{"Mecca", "مكه", "مكةالمكرمة", "الرياض", "", "  ", "مكة المكرمة الجديدة"}) {
            Property outside = property(PropertyType.LAND, ListingType.SALE, null, city, "النوارية", "150", "100");
            outside.setFormattedAddress("مكة المكرمة");
            outside.setNeighborhood("مكة");
        }
        perform(needs()).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5));
    }

    @Test
    void districtWhitespaceAndExactlyOneHiPrefixAreComparisonOnly() throws Exception {
        Property wanted = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "  حي\u00a0النوارية\t الشمالية  ", "150", "100");
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "حي حي النوارية الشمالية", "150", "100");
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية الجنوبيه", "150", "100");
        Map<String, Object> body = needs();
        body.put("district", " النوارية   الشمالية ");
        body.put("districtRequirement", "REQUIRED");
        properties.flush();
        entities.clear();
        LocalDateTime beforeUpdate = properties.findById(wanted.getId()).orElseThrow().getUpdatedAt();
        long beforeCount = properties.count();
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(wanted.getId()))
                .andExpect(jsonPath("$.content[0].district").value("  حي\u00a0النوارية\t الشمالية  "))
                .andExpect(jsonPath("$.content[0].districtMatch").value("MATCHED"));
        properties.flush();
        entities.clear();
        Property unchanged = properties.findById(wanted.getId()).orElseThrow();
        assertEquals("  حي\u00a0النوارية\t الشمالية  ", unchanged.getDistrict());
        assertEquals("مكة", unchanged.getCity());
        assertEquals(beforeUpdate, unchanged.getUpdatedAt());
        assertEquals(beforeCount, properties.count());
    }

    @Test
    void districtSqlWildcardsAndArabicLettersRemainLiteral() throws Exception {
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "ال_%نوارية", "150", "100");
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "150", "100");
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "حياة", "150", "100");
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "حيالياسمين", "150", "100");
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية الجنوبية", "150", "100");
        Map<String, Object> body = needs();
        body.put("districtRequirement", "REQUIRED");
        for (String literal : new String[]{"ال_%نوارية", "حياة", "حيالياسمين"}) {
            body.put("district", literal);
            perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].district").value(literal));
        }
        body.put("district", "النوارية الجنوبيه");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void requiredUnknownDistrictIsExcludedAndPreferredUnknownStaysWithoutFallback() throws Exception {
        Property unknown = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "حي", "150", "100");
        unknown.setNeighborhood("النوارية");
        unknown.setFormattedAddress("النوارية، مكة المكرمة");
        unknown.setDescription("حي النوارية");
        properties.flush();
        Map<String, Object> body = needs();
        body.put("district", "النوارية");
        body.put("districtRequirement", "REQUIRED");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        body.put("districtRequirement", "PREFERRED");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].districtMatch").value("UNKNOWN"))
                .andExpect(jsonPath("$.content[0].preferredCount").value(1))
                .andExpect(jsonPath("$.content[0].preferredMatchedCount").value(0));
        body.put("district", "حي");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.content[0].districtMatch").value("UNKNOWN"));
        body.put("districtRequirement", "REQUIRED");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void areaBoundariesAreInclusiveAndWholePreferredRangeCountsOnce() throws Exception {
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "100", "100");
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "200", "100");
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "99.99", "100");
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "200.01", "100");
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "0", "100");
        Map<String, Object> body = needs();
        body.put("minArea", "100");
        body.put("maxArea", "200");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.needs.areaRequirement").value("REQUIRED"))
                .andExpect(jsonPath("$.content[0].areaMatch").value("MATCHED"));
        body.put("areaRequirement", "PREFERRED");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5))
                .andExpect(jsonPath("$.content[0].preferredCount").value(1))
                .andExpect(jsonPath("$.content[0].preferredMatchedCount").value(1))
                .andExpect(jsonPath("$.content[2].areaMatch").value("UNKNOWN"))
                .andExpect(jsonPath("$.content[2].area").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.content[2].preferredMatchedCount").value(0));
        body.put("minArea", "200");
        body.put("maxArea", "200");
        body.put("areaRequirement", "REQUIRED");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].area").value("200.00"));
    }

    @Test
    void searchGoalNeverInfersPurposeOrInvestmentValue() throws Exception {
        Property residential = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "150", "100");
        residential.setPurpose(PropertyPurpose.RESIDENTIAL);
        Property commercial = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "150", "100");
        commercial.setPurpose(PropertyPurpose.COMMERCIAL);
        properties.flush();
        Map<String, Object> body = needs();
        body.put("searchGoal", "INVESTMENT");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].purpose").doesNotExist())
                .andExpect(jsonPath("$.content[0].investmentRating").doesNotExist());
    }

    @Test
    void ranksEveryCandidateBeforePaginationNotJustFirstHundred() throws Exception {
        for (int i = 0; i < 120; i++) {
            property(PropertyType.LAND, ListingType.SALE, null, "مكة", "العزيزية", "250", "100");
        }
        Property best = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "150", "100");
        createdAt(best, LocalDateTime.of(2020, 1, 1, 0, 0));
        Map<String, Object> body = needs();
        body.put("district", "النوارية");
        body.put("districtRequirement", "PREFERRED");
        body.put("minArea", "100");
        body.put("maxArea", "200");
        body.put("areaRequirement", "PREFERRED");
        perform(body, buyerAccess, 0, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(121))
                .andExpect(jsonPath("$.totalPages").value(121))
                .andExpect(jsonPath("$.content[0].id").value(best.getId()))
                .andExpect(jsonPath("$.content[0].preferredMatchedCount").value(2))
                .andExpect(jsonPath("$.content[0].preferredCount").value(2));
        perform(body, buyerAccess, 1, 1).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].preferredMatchedCount").value(0));
    }

    @Test
    void yearlyRentalKeepsItsPeriodAndSingleAreaBoundsRemainInclusive() throws Exception {
        property(PropertyType.APARTMENT, ListingType.RENT, RentalPeriod.YEARLY, "مكة", "النوارية", "150", "45000");
        property(PropertyType.APARTMENT, ListingType.RENT, RentalPeriod.YEARLY, "مكة", "النوارية", "200", "45000");
        property(PropertyType.APARTMENT, ListingType.RENT, RentalPeriod.MONTHLY, "مكة", "النوارية", "150", "3750");
        Map<String, Object> body = needs();
        body.put("type", "APARTMENT");
        body.put("listingType", "RENT");
        body.put("rentalPeriod", "YEARLY");
        body.put("maxBudget", "45000");
        body.put("minArea", "200");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].rentalPeriod").value("YEARLY"))
                .andExpect(jsonPath("$.content[0].area").value("200.00"));
        body.remove("minArea");
        body.put("maxArea", "150");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].area").value("150.00"));
    }

    @Test
    void districtAndWholeAreaRangeHaveEqualWeightAndOnlyPreferenceMatchesAreCounted() throws Exception {
        Property both = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "150", "100");
        Property districtOnly = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "250", "100");
        Property areaOnly = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "العزيزية", "150", "100");
        createdAt(both, LocalDateTime.of(2020, 1, 1, 0, 0));
        createdAt(districtOnly, LocalDateTime.of(2024, 1, 1, 0, 0));
        createdAt(areaOnly, LocalDateTime.of(2025, 1, 1, 0, 0));
        Map<String, Object> body = needs();
        body.put("district", "النوارية");
        body.put("minArea", "100");
        body.put("maxArea", "200");
        body.put("areaRequirement", "PREFERRED");
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(both.getId()))
                .andExpect(jsonPath("$.content[0].preferredMatchedCount").value(2))
                .andExpect(jsonPath("$.content[1].id").value(areaOnly.getId()))
                .andExpect(jsonPath("$.content[1].preferredMatchedCount").value(1))
                .andExpect(jsonPath("$.content[1].districtMatch").value("MISMATCHED"))
                .andExpect(jsonPath("$.content[1].preferredCount").value(2))
                .andExpect(jsonPath("$.content[2].id").value(districtOnly.getId()))
                .andExpect(jsonPath("$.content[2].preferredMatchedCount").value(1))
                .andExpect(jsonPath("$.content[2].areaMatch").value("MISMATCHED"));
        body.put("districtRequirement", "REQUIRED");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[0].preferredCount").value(1))
                .andExpect(jsonPath("$.content[0].preferredMatchedCount").value(1));
    }

    @Test
    void newerComesBeforeOlderAndIdBreaksTiesWithNullDatesLast() throws Exception {
        Property older = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "150", "100");
        Property newerLowId = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "150", "100");
        Property newerHighId = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "150", "100");
        Property nullDate = property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "150", "100");
        createdAt(older, LocalDateTime.of(2024, 1, 1, 0, 0));
        createdAt(newerLowId, LocalDateTime.of(2025, 1, 1, 0, 0));
        createdAt(newerHighId, LocalDateTime.of(2025, 1, 1, 0, 0));
        createdAt(nullDate, null);
        for (int i = 0; i < 2; i++) {
            perform(needs()).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content[0].id").value(newerHighId.getId()))
                    .andExpect(jsonPath("$.content[1].id").value(newerLowId.getId()))
                    .andExpect(jsonPath("$.content[2].id").value(older.getId()))
                    .andExpect(jsonPath("$.content[3].id").value(nullDate.getId()));
        }
        perform(needs(), buyerAccess, Integer.MAX_VALUE, 20).andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(4));
    }

    @Test
    void pricesAndAreasRemainExactDecimalStrings() throws Exception {
        property(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "9999999999.99", "99999999999999999.99");
        Map<String, Object> body = needs();
        body.put("maxBudget", "99999999999999999.99");
        body.put("minArea", "9999999999.99");
        body.put("maxArea", "9999999999.99");
        perform(body).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].price").value("99999999999999999.99"))
                .andExpect(jsonPath("$.content[0].area").value("9999999999.99"));
    }

    @ParameterizedTest
    @CsvSource({"SALE,", "RENT,MONTHLY", "RENT,YEARLY"})
    void smallestPositiveBudgetIncludesEqualPriceButExcludesZeroNegativeAndOneCentAbove(
            ListingType listingType, RentalPeriod period) throws Exception {
        Property equal = property(PropertyType.LAND, listingType, period, "مكة", "النوارية", "0.01", "0.01");
        property(PropertyType.LAND, listingType, period, "مكة", "النوارية", "0.01", "0.02");
        property(PropertyType.LAND, listingType, period, "مكة", "النوارية", "0.01", "0.00");
        property(PropertyType.LAND, listingType, period, "مكة", "النوارية", "0.01", "-0.01");
        Map<String, Object> body = needs();
        body.put("listingType", listingType.name());
        body.put("rentalPeriod", period == null ? null : period.name());
        body.put("maxBudget", "0.01");
        body.put("minArea", "0.01");
        body.put("maxArea", "0.01");
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].id").value(equal.getId()))
                .andExpect(jsonPath("$.content[0].price").value("0.01"))
                .andExpect(jsonPath("$.content[0].area").value("0.01"));
    }

    @ParameterizedTest
    @CsvSource({"SALE,", "RENT,MONTHLY", "RENT,YEARLY"})
    void budgetComparisonRemainsExactBeyondJavascriptSafePrecision(
            ListingType listingType, RentalPeriod period) throws Exception {
        Property below = property(PropertyType.LAND, listingType, period, "مكة", "النوارية", "150.25", "9007199254740993.00");
        Property equal = property(PropertyType.LAND, listingType, period, "مكة", "النوارية", "150.25", "9007199254740993.01");
        property(PropertyType.LAND, listingType, period, "مكة", "النوارية", "150.25", "9007199254740993.02");
        createdAt(below, LocalDateTime.of(2024, 1, 1, 0, 0));
        createdAt(equal, LocalDateTime.of(2025, 1, 1, 0, 0));
        Map<String, Object> body = needs();
        body.put("listingType", listingType.name());
        body.put("rentalPeriod", period == null ? null : period.name());
        body.put("maxBudget", "9007199254740993.01");
        perform(body).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.needs.maxBudget").value("9007199254740993.01"))
                .andExpect(jsonPath("$.content[0].id").value(equal.getId()))
                .andExpect(jsonPath("$.content[0].price").value("9007199254740993.01"))
                .andExpect(jsonPath("$.content[1].id").value(below.getId()))
                .andExpect(jsonPath("$.content[1].price").value("9007199254740993.00"));
    }

    @Test
    void repeatedPagesOfDifferentSizesKeepCompleteOrderWithoutDuplicatesOrPersistentChanges() throws Exception {
        for (int i = 0; i < 9; i++) {
            Property property = property(PropertyType.LAND, ListingType.SALE, null, "مكة",
                    i % 2 == 0 ? "  حي النوارية  " : "العزيزية", i % 3 == 0 ? "150.25" : "250.25", "100.01");
            property.setGooglePlaceId("structured-place-" + i);
            property.setFormattedAddress("عنوان محفوظ " + i);
            property.setNeighborhood("حي محفوظ " + i);
            property.setDescription("وصف لا تتغير بياناته " + i);
            property.setLatitude(new BigDecimal("21.5101000"));
            property.setLongitude(new BigDecimal("39.9962000"));
            createdAt(property, i == 8 ? null : LocalDateTime.of(2024 + i % 2, 1, 1, 0, 0));
        }
        Map<String, Object> body = needs();
        body.put("district", "النوارية");
        body.put("districtRequirement", "PREFERRED");
        body.put("minArea", "100");
        body.put("maxArea", "200");
        body.put("areaRequirement", "PREFERRED");
        List<List<Object>> before = persistedSnapshot();
        JsonNode all = response(perform(body, buyerAccess, 0, 20).andExpect(status().isOk()));
        List<Long> expected = ids(all);
        assertEquals(9, expected.size());
        for (int size : new int[]{1, 2, 4, 20}) {
            for (int repetition = 0; repetition < 2; repetition++) {
                List<Long> paged = new ArrayList<>();
                int pageCount = (expected.size() + size - 1) / size;
                for (int page = 0; page < pageCount; page++) {
                    JsonNode result = response(perform(body, buyerAccess, page, size).andExpect(status().isOk()));
                    assertEquals(expected.size(), result.get("totalElements").asInt());
                    assertEquals(pageCount, result.get("totalPages").asInt());
                    paged.addAll(ids(result));
                }
                assertEquals(expected, paged);
                assertEquals(paged.size(), new HashSet<>(paged).size());
            }
        }
        assertEquals(before, persistedSnapshot(), "Matching must not rewrite any persisted property column");
    }

    @Test
    void exactlyThousandCoreCandidatesAreRankedButThousandAndOneRejectsBeforeLocation() throws Exception {
        List<Property> rows = new ArrayList<>();
        for (int i = 0; i < 1000; i++) {
            rows.add(unsaved(PropertyType.LAND, ListingType.SALE, null, "مكة", "النوارية", "150", "100"));
        }
        properties.saveAllAndFlush(rows);
        perform(needs(), buyerAccess, 0, 20).andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1000))
                .andExpect(jsonPath("$.totalPages").value(50))
                .andExpect(jsonPath("$.content", hasSize(20)));
        property(PropertyType.LAND, ListingType.SALE, null, "الرياض", "النوارية", "150", "100");
        perform(needs()).andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.content").doesNotExist());
        Map<String, Object> narrowed = needs();
        narrowed.put("maxBudget", "99.99");
        perform(narrowed).andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    private ResultActions perform(Map<String, Object> body) throws Exception {
        return perform(body, buyerAccess, 0, 10);
    }

    private ResultActions perform(Map<String, Object> body, Cookie access, int page, int size) throws Exception {
        properties.flush();
        entities.clear();
        return mvc.perform(post(ENDPOINT).with(csrf().asHeader()).cookie(access)
                .param("page", Integer.toString(page)).param("size", Integer.toString(size))
                .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)));
    }

    private Map<String, Object> needs() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("type", "LAND");
        body.put("listingType", "SALE");
        body.put("maxBudget", "500000.00");
        return body;
    }

    private User user(String email, Role role) {
        User user = new User();
        user.setName("Matching test");
        user.setEmail(email);
        user.setPassword("unused-hash");
        user.setRole(role);
        return users.saveAndFlush(user);
    }

    private Cookie access(User user) {
        return new Cookie(AuthCookieService.ACCESS, jwt.generateAccessToken(user.getEmail()));
    }

    private Property property(PropertyType type, ListingType listing, RentalPeriod period, String city,
                              String district, String area, String price) {
        return properties.saveAndFlush(unsaved(type, listing, period, city, district, area, price));
    }

    private Property unsaved(PropertyType type, ListingType listing, RentalPeriod period, String city,
                             String district, String area, String price) {
        Property property = new Property();
        property.setPublisher(entities.getReference(User.class, admin.getId()));
        property.setTitle("Matching property");
        property.setType(type);
        property.setListingType(listing);
        property.setRentalPeriod(period);
        property.setCity(city);
        property.setDistrict(district);
        property.setArea(new BigDecimal(area));
        property.setPrice(new BigDecimal(price));
        property.setPurpose(PropertyPurpose.RESIDENTIAL);
        property.setStatus(PropertyStatus.ACTIVE);
        return property;
    }

    private void createdAt(Property property, LocalDateTime date) {
        Property managed = entities.find(Property.class, property.getId());
        ReflectionTestUtils.setField(managed, "createdAt", date);
        properties.flush();
    }

    private JsonNode response(ResultActions result) throws Exception {
        return mapper.readTree(result.andReturn().getResponse().getContentAsString());
    }

    private List<Long> ids(JsonNode result) {
        List<Long> ids = new ArrayList<>();
        result.get("content").forEach(property -> ids.add(property.get("id").asLong()));
        return ids;
    }

    private List<List<Object>> persistedSnapshot() {
        properties.flush();
        entities.clear();
        List<?> rows = entities.createNativeQuery("SELECT id, seller_id, title, type, listing_type, rental_period, "
                + "city, district, google_place_id, formatted_address, neighborhood, latitude, longitude, area, price, "
                + "street_width, facade, purpose, description, status, created_at, updated_at "
                + "FROM properties ORDER BY id").getResultList();
        List<List<Object>> snapshot = new ArrayList<>();
        for (Object row : rows) {
            snapshot.add(Collections.unmodifiableList(new ArrayList<>(Arrays.asList((Object[]) row))));
        }
        return List.copyOf(snapshot);
    }
}
