package com.rushd.controller;

import com.rushd.entity.Property;
import com.rushd.entity.PropertyFacade;
import com.rushd.entity.PropertyPurpose;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.PropertyType;
import com.rushd.entity.Role;
import com.rushd.entity.User;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyListingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    private User seller;

    @BeforeEach
    void setUp() {
        seller = new User();
        seller.setName("Listing Seller");
        seller.setEmail("listing-seller@rushd.com");
        seller.setPassword("$2a$10$irrelevant-hash");
        seller.setRole(Role.SELLER);
        seller = userRepository.saveAndFlush(seller);

        saveProperty("Riyadh Land", "Riyadh", "Al Yasmin", PropertyType.LAND,
                "500000", PropertyStatus.ACTIVE, LocalDateTime.of(2026, 1, 1, 10, 0));
        saveProperty("Riyadh Villa", "Riyadh", "Al Narjis", PropertyType.VILLA,
                "2000000", PropertyStatus.ACTIVE, LocalDateTime.of(2026, 1, 3, 10, 0));
        saveProperty("Jeddah Apartment", "Jeddah", "Al Hamra", PropertyType.APARTMENT,
                "1000000", PropertyStatus.ACTIVE, LocalDateTime.of(2026, 1, 2, 10, 0));
        saveProperty("Hidden Inactive", "Riyadh", "Al Yasmin", PropertyType.LAND,
                "750000", PropertyStatus.INACTIVE, LocalDateTime.of(2026, 1, 4, 10, 0));
        saveProperty("Hidden Draft", "Riyadh", "Al Narjis", PropertyType.LAND,
                "250000", PropertyStatus.DRAFT, LocalDateTime.of(2026, 1, 5, 10, 0));
    }

    @Test
    void listPropertiesWithoutFiltersIsPublic() throws Exception {
        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    void onlyActivePropertiesAreReturned() throws Exception {
        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].status", everyItem(is("ACTIVE"))))
                .andExpect(jsonPath("$.content[?(@.title == 'Hidden Inactive')]").isEmpty())
                .andExpect(jsonPath("$.content[?(@.title == 'Hidden Draft')]").isEmpty())
                .andExpect(jsonPath("$.content[0].password").doesNotExist());
    }

    @Test
    void filtersByCity() throws Exception {
        mockMvc.perform(get("/api/properties").param("city", "riyadh"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.content[*].city", everyItem(is("Riyadh"))));
    }

    @Test
    void filtersByDistrict() throws Exception {
        mockMvc.perform(get("/api/properties").param("district", "Al Yasmin"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Riyadh Land"));
    }

    @Test
    void filtersByPropertyType() throws Exception {
        mockMvc.perform(get("/api/properties").param("type", "VILLA"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Riyadh Villa"));
    }

    @Test
    void filtersByMinimumPriceInclusively() throws Exception {
        mockMvc.perform(get("/api/properties").param("minPrice", "1000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void filtersByMaximumPriceInclusively() throws Exception {
        mockMvc.perform(get("/api/properties").param("maxPrice", "1000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    void combinesMultipleFilters() throws Exception {
        mockMvc.perform(get("/api/properties")
                        .param("city", "Riyadh")
                        .param("district", "Al Yasmin")
                        .param("type", "LAND")
                        .param("minPrice", "400000")
                        .param("maxPrice", "600000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Riyadh Land"));
    }

    @Test
    void paginationWorks() throws Exception {
        mockMvc.perform(get("/api/properties")
                        .param("page", "1")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].title").value("Jeddah Apartment"))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(3))
                .andExpect(jsonPath("$.first").value(false))
                .andExpect(jsonPath("$.last").value(false));
    }

    @Test
    void defaultSortingIsCreatedAtDescending() throws Exception {
        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Riyadh Villa"))
                .andExpect(jsonPath("$.content[1].title").value("Jeddah Apartment"))
                .andExpect(jsonPath("$.content[2].title").value("Riyadh Land"));
    }

    @Test
    void requestedSortingIsApplied() throws Exception {
        mockMvc.perform(get("/api/properties").param("sort", "price,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].title").value("Riyadh Land"))
                .andExpect(jsonPath("$.content[1].title").value("Jeddah Apartment"))
                .andExpect(jsonPath("$.content[2].title").value("Riyadh Villa"));
    }

    @Test
    void invalidPriceRangeReturns400() throws Exception {
        mockMvc.perform(get("/api/properties")
                        .param("minPrice", "2000000")
                        .param("maxPrice", "500000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"minPrice", "maxPrice"})
    void negativePriceReturns400(String parameter) throws Exception {
        mockMvc.perform(get("/api/properties").param(parameter, "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void invalidPropertyTypeReturns400() throws Exception {
        mockMvc.perform(get("/api/properties").param("type", "HOUSE"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.message").value("Invalid value for parameter: type"));
    }

    @Test
    void negativePageReturns400() throws Exception {
        mockMvc.perform(get("/api/properties").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "101"})
    void invalidSizeReturns400(String size) throws Exception {
        mockMvc.perform(get("/api/properties").param("size", size))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void unsupportedSortReturns400() throws Exception {
        mockMvc.perform(get("/api/properties").param("sort", "seller.password,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    void createPropertyRemainsProtected() throws Exception {
        mockMvc.perform(post("/api/properties").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isUnauthorized());
    }

    @ParameterizedTest
    @ValueSource(strings = {"مكة", "مكة المكرمة", "Makkah", " mAkKaH ", "مكة  المكرمة", "\u2003مكة\tالمكرمة\u00a0"})
    void approvedMakkahCityAliasesIncludeAllStoredVariantsWithoutRewritingThem(String city) throws Exception {
        List<String> stored = List.of("مكة", "مكة المكرمة", "Makkah", " MaKKaH ", "مكة  المكرمة", "\u2003مكة\tالمكرمة\u00a0");
        List<Long> ids = new ArrayList<>();
        for (String variant : stored) ids.add(saveProperty("Makkah alias", variant, "الياسمين", PropertyType.LAND,
                "1000.00", PropertyStatus.ACTIVE, LocalDateTime.of(2026, 2, 1, 10, 0)).getId());
        mockMvc.perform(get("/api/properties").param("city", city))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(6));
        for (int index = 0; index < stored.size(); index++) {
            assertEquals(stored.get(index), propertyRepository.findById(ids.get(index)).orElseThrow().getCity());
        }
    }

    @Test
    void nonMakkahCitiesAndDistrictsKeepTheirExistingExactComparison() throws Exception {
        saveProperty("No internal space guessing", "Riyadh  City", "حي الياسمين", PropertyType.LAND,
                "1000.00", PropertyStatus.ACTIVE, LocalDateTime.of(2026, 2, 1, 10, 0));
        mockMvc.perform(get("/api/properties").param("city", "Riyadh City"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/properties").param("city", "Riyadh  City").param("district", "الياسمين"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mockMvc.perform(get("/api/properties").param("city", "Riyadh  City").param("district", " حي الياسمين "))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"مكه", "Mecca", "مكة الجديدة"})
    void cityFilterDoesNotGuessUnapprovedAliases(String city) throws Exception {
        saveProperty("Makkah only", "مكة", "الياسمين", PropertyType.LAND,
                "1000.00", PropertyStatus.ACTIVE, LocalDateTime.of(2026, 2, 1, 10, 0));
        mockMvc.perform(get("/api/properties").param("city", city))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void catalogPriceBoundsRemainInclusiveAndExactBeyondJavascriptPrecision() throws Exception {
        saveProperty("Exact lower", "مكة", "الياسمين", PropertyType.LAND,
                "9007199254740993.01", PropertyStatus.ACTIVE, LocalDateTime.of(2026, 2, 1, 10, 0));
        saveProperty("Exact higher", "Makkah", "الياسمين", PropertyType.LAND,
                "9007199254740993.02", PropertyStatus.ACTIVE, LocalDateTime.of(2026, 2, 1, 10, 0));
        saveProperty("Smallest price", "مكة المكرمة", "الياسمين", PropertyType.LAND,
                "0.01", PropertyStatus.ACTIVE, LocalDateTime.of(2026, 2, 1, 10, 0));
        saveProperty("Largest price", "مكة", "الياسمين", PropertyType.LAND,
                "99999999999999999.99", PropertyStatus.ACTIVE, LocalDateTime.of(2026, 2, 1, 10, 0));
        mockMvc.perform(get("/api/properties").param("city", "مكة").param("listingType", "SALE")
                        .param("district", "الياسمين").param("type", "LAND")
                        .param("minPrice", "9007199254740993.01").param("maxPrice", "9007199254740993.01"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].price").value("9007199254740993.01"));
        for (String boundary : List.of("0.01", "99999999999999999.99")) {
            mockMvc.perform(get("/api/properties").param("city", "مكة").param("minPrice", boundary).param("maxPrice", boundary))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                    .andExpect(jsonPath("$.content[0].price").value(boundary));
        }
        mockMvc.perform(get("/api/properties").param("city", "مكة").param("minPrice", "0").param("maxPrice", "0"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"createdAt,asc", "createdAt,desc", "price,asc", "price,desc", "title,asc", "title,desc"})
    void tiedSortValuesUseDescendingIdAcrossPages(String sort) throws Exception {
        List<Long> ids = new ArrayList<>();
        for (int index = 0; index < 5; index++) ids.add(saveProperty("Tied catalog", "مكة", "الياسمين", PropertyType.LAND,
                "1000.00", PropertyStatus.ACTIVE, LocalDateTime.of(2026, 2, 1, 10, 0)).getId());
        Collections.reverse(ids);
        List<Long> actual = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            var response = mockMvc.perform(get("/api/properties").param("city", "مكة").param("sort", sort)
                            .param("size", "2").param("page", String.valueOf(page)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(5)).andReturn();
            var content = new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("content");
            content.forEach(property -> actual.add(property.get("id").longValue()));
        }
        assertEquals(ids, actual);
        mockMvc.perform(get("/api/properties").param("city", "مكة").param("sort", "id,asc").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content[0].id").value(ids.get(ids.size() - 1)));
    }

    @Test
    void outOfRangeCatalogPageKeepsFiltersAndReportsCurrentTotals() throws Exception {
        mockMvc.perform(get("/api/properties").param("city", "Riyadh").param("page", "100").param("size", "12"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.content.length()").value(0))
                .andExpect(jsonPath("$.page").value(100)).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1)).andExpect(jsonPath("$.last").value(true));
    }

    private Property saveProperty(String title,
                              String city,
                              String district,
                              PropertyType type,
                              String price,
                              PropertyStatus status,
                              LocalDateTime createdAt) {
        Property property = new Property();
        property.setPublisher(seller);
        property.setTitle(title);
        property.setType(type);
        property.setCity(city);
        property.setDistrict(district);
        property.setArea(new BigDecimal("500"));
        property.setPrice(new BigDecimal(price));
        property.setStreetWidth(new BigDecimal("20"));
        property.setFacade(PropertyFacade.NORTH);
        property.setPurpose(PropertyPurpose.RESIDENTIAL);
        property.setStatus(status);

        property = propertyRepository.saveAndFlush(property);
        ReflectionTestUtils.setField(property, "createdAt", createdAt);
        propertyRepository.saveAndFlush(property);
        return property;
    }
}
