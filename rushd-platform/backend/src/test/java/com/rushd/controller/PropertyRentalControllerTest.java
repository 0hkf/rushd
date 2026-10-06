package com.rushd.controller;

import com.rushd.entity.*;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import com.rushd.service.JwtService;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyRentalControllerTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private PropertyRepository propertyRepository;
    private User admin;
    private User buyer;
    private User legacySeller;

    @BeforeEach
    void setUp() {
        admin = saveUser("rental-admin@example.com", Role.ADMIN);
        buyer = saveUser("rental-user@example.com", Role.BUYER);
        legacySeller = saveUser("legacy-user@example.com", Role.SELLER);
    }

    @ParameterizedTest
    @ValueSource(strings = {"MONTHLY", "YEARLY"})
    void adminCanCreateRentalWithCorrectPeriod(String period) throws Exception {
        mockMvc.perform(post("/api/properties").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, token(admin)))
                        .contentType(MediaType.APPLICATION_JSON).content(request("RENT", period)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingType").value("RENT"))
                .andExpect(jsonPath("$.rentalPeriod").value(period))
                .andExpect(jsonPath("$.publisherId").value(admin.getId()))
                .andExpect(jsonPath("$.sellerId").doesNotExist());
        Property saved = propertyRepository.findAll().get(0);
        assertEquals(ListingType.RENT, saved.getListingType());
        assertEquals(RentalPeriod.valueOf(period), saved.getRentalPeriod());
        assertEquals(admin.getId(), saved.getPublisher().getId());
    }

    @Test
    void rentalRequiresPeriodAndSaleRejectsPeriod() throws Exception {
        for (String body : new String[]{request("RENT", null), request("SALE", "MONTHLY")}) {
            mockMvc.perform(post("/api/properties").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, token(admin)))
                            .contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message.rentalPricingValid").exists());
        }
        assertEquals(0, propertyRepository.count());
    }

    @Test
    void existingPropertyDefaultsToSaleAndCanBeUpdatedToRental() throws Exception {
        Property property = saveProperty(admin, PropertyStatus.ACTIVE, ListingType.SALE, null);
        assertEquals(ListingType.SALE, property.getListingType());
        mockMvc.perform(put("/api/properties/{id}", property.getId()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, token(admin)))
                        .contentType(MediaType.APPLICATION_JSON).content(request("RENT", "YEARLY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.listingType").value("RENT"))
                .andExpect(jsonPath("$.rentalPeriod").value("YEARLY"));
        assertEquals(RentalPeriod.YEARLY, propertyRepository.findById(property.getId()).orElseThrow().getRentalPeriod());
    }

    @Test
    void oldCreatePayloadDefaultsToSaleWithoutLocation() throws Exception {
        mockMvc.perform(post("/api/properties").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, token(admin)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Old payload\",\"type\":\"LAND\",\"city\":\"Riyadh\",\"district\":\"Al Yasmin\",\"area\":150,\"price\":60000,\"purpose\":\"RESIDENTIAL\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.listingType").value("SALE"))
                .andExpect(jsonPath("$.rentalPeriod").isEmpty())
                .andExpect(jsonPath("$.googlePlaceId").isEmpty());
    }

    @Test
    void adminCanEditLegacyPropertyWithoutChangingPublisher() throws Exception {
        Property property = saveProperty(legacySeller, PropertyStatus.ACTIVE, ListingType.SALE, null);
        mockMvc.perform(put("/api/properties/{id}", property.getId()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, token(admin))).contentType(MediaType.APPLICATION_JSON)
                        .content(request("RENT", "MONTHLY")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publisherId").value(legacySeller.getId()))
                .andExpect(jsonPath("$.listingType").value("RENT"));
        assertEquals(legacySeller.getId(), propertyRepository.findById(property.getId()).orElseThrow().getPublisher().getId());
    }

    @Test
    void publicCatalogFiltersByListingAndRentalPeriod() throws Exception {
        saveProperty(admin, PropertyStatus.ACTIVE, ListingType.SALE, null);
        saveProperty(admin, PropertyStatus.ACTIVE, ListingType.RENT, RentalPeriod.MONTHLY);
        saveProperty(admin, PropertyStatus.ACTIVE, ListingType.RENT, RentalPeriod.YEARLY);
        mockMvc.perform(get("/api/properties").param("listingType", "RENT").param("rentalPeriod", "MONTHLY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.content[0].rentalPeriod").value("MONTHLY"));
    }

    @Test
    void managementListRequiresAdminAndIncludesDrafts() throws Exception {
        saveProperty(admin, PropertyStatus.DRAFT, ListingType.RENT, RentalPeriod.YEARLY);
        mockMvc.perform(get("/api/admin/properties")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/properties").cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, token(buyer))))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/properties").cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, token(admin))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
        mockMvc.perform(get("/api/properties"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void legacySellerCannotUpdateEvenTheirOwnPropertyOrViewDraft() throws Exception {
        Property property = saveProperty(legacySeller, PropertyStatus.DRAFT, ListingType.SALE, null);
        mockMvc.perform(put("/api/properties/{id}", property.getId()).with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf())
                        .cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, token(legacySeller))).contentType(MediaType.APPLICATION_JSON)
                        .content(request("SALE", null))).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/properties/{id}", property.getId())
                        .cookie(new jakarta.servlet.http.Cookie(com.rushd.service.AuthCookieService.ACCESS, token(legacySeller)))).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELLER", "ADMIN"})
    void publicSignupCannotCreatePrivilegedRoles(String role) throws Exception {
        mockMvc.perform(post("/api/auth/register").with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"New user\",\"email\":\"new@example.com\",\"password\":\"Password123\",\"role\":\"" + role + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.publicRoleValid").exists());
        assertTrue(userRepository.findByEmail("new@example.com").isEmpty());
    }

    private User saveUser(String email, Role role) {
        User user = new User();
        user.setName("Test User");
        user.setEmail(email);
        user.setPassword("test-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private Property saveProperty(User publisher, PropertyStatus status, ListingType listingType, RentalPeriod period) {
        Property property = new Property();
        property.setPublisher(publisher);
        property.setTitle("Rental test property");
        property.setType(PropertyType.APARTMENT);
        property.setCity("Riyadh");
        property.setDistrict("Al Yasmin");
        property.setArea(new BigDecimal("150"));
        property.setPrice(new BigDecimal("60000"));
        property.setPurpose(PropertyPurpose.RESIDENTIAL);
        property.setStatus(status);
        property.setListingType(listingType);
        property.setRentalPeriod(period);
        return propertyRepository.saveAndFlush(property);
    }

    private String token(User user) {
        return jwtService.generateAccessToken(user.getEmail());
    }

    private String request(String type, String period) {
        return "{\"title\":\"Rental property\",\"type\":\"APARTMENT\",\"city\":\"Riyadh\",\"district\":\"Al Yasmin\",\"area\":150,\"price\":60000,\"purpose\":\"RESIDENTIAL\",\"status\":\"ACTIVE\",\"listingType\":\"" + type + "\",\"rentalPeriod\":" + (period == null ? "null" : "\"" + period + "\"") + "}";
    }
}
