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
import com.rushd.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyUpdateControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PropertyRepository propertyRepository;

    private User owner;
    private User otherSeller;
    private User buyer;
    private User admin;
    private Property property;

    @BeforeEach
    void setUp() {
        owner = saveUser("Owner Seller", "update-owner@rushd.com", Role.SELLER);
        otherSeller = saveUser("Other Seller", "update-other@rushd.com", Role.SELLER);
        buyer = saveUser("Rushd Buyer", "update-buyer@rushd.com", Role.BUYER);
        admin = saveUser("Rushd Admin", "update-admin@rushd.com", Role.ADMIN);
        property = saveProperty();
    }

    @Test
    void ownerCanUpdateProperty() throws Exception {
        putAs(owner, validUpdateRequest("Owner Updated Villa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Owner Updated Villa"))
                .andExpect(jsonPath("$.sellerId").value(owner.getId()));

        Property updated = propertyRepository.findById(property.getId()).orElseThrow();
        assertEquals("Owner Updated Villa", updated.getTitle());
        assertEquals(owner.getId(), updated.getSeller().getId());
    }

    @Test
    void adminCanUpdateProperty() throws Exception {
        putAs(admin, validUpdateRequest("Admin Updated Villa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Admin Updated Villa"))
                .andExpect(jsonPath("$.sellerId").value(owner.getId()));
    }

    @Test
    void buyerCannotUpdateProperty() throws Exception {
        putAs(buyer, validUpdateRequest("Buyer Update"))
                .andExpect(status().isForbidden());

        assertEquals("Original Villa", propertyRepository.findById(property.getId()).orElseThrow().getTitle());
    }

    @Test
    void anotherSellerCannotUpdateProperty() throws Exception {
        putAs(otherSeller, validUpdateRequest("Other Seller Update"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        assertEquals("Original Villa", propertyRepository.findById(property.getId()).orElseThrow().getTitle());
    }

    @Test
    void suppliedSellerIdCannotChangeOwnership() throws Exception {
        String request = validUpdateRequest("Still Owned Villa")
                .replace("{", "{\n  \"sellerId\": " + otherSeller.getId() + ",");

        putAs(owner, request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sellerId").value(owner.getId()));

        Property updated = propertyRepository.findById(property.getId()).orElseThrow();
        assertEquals(owner.getId(), updated.getSeller().getId());
    }

    @Test
    void ownerCanUpdateStructuredLocation() throws Exception {
        putAs(owner, updateRequestWithLocation())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googlePlaceId").value("ChIJUpdatedPlace456"))
                .andExpect(jsonPath("$.formattedAddress").value("زهرة العمرة، مكة، السعودية"))
                .andExpect(jsonPath("$.neighborhood").value("زهرة العمرة"))
                .andExpect(jsonPath("$.latitude").value(21.5101))
                .andExpect(jsonPath("$.longitude").value(39.9962));

        Property updated = propertyRepository.findById(property.getId()).orElseThrow();
        assertEquals("ChIJUpdatedPlace456", updated.getGooglePlaceId());
        assertEquals("زهرة العمرة، مكة، السعودية", updated.getFormattedAddress());
        assertEquals("زهرة العمرة", updated.getNeighborhood());
        assertEquals(0, new BigDecimal("21.5101000").compareTo(updated.getLatitude()));
        assertEquals(0, new BigDecimal("39.9962000").compareTo(updated.getLongitude()));
    }

    @Test
    void updateCanLeaveStructuredLocationEmpty() throws Exception {
        putAs(owner, validUpdateRequest("No Location Villa"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.googlePlaceId").value((Object) null))
                .andExpect(jsonPath("$.latitude").value((Object) null));

        Property updated = propertyRepository.findById(property.getId()).orElseThrow();
        assertNull(updated.getGooglePlaceId());
        assertNull(updated.getLatitude());
    }

    private ResultActions putAs(User user, String request) throws Exception {
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return mockMvc.perform(put("/api/properties/{id}", property.getId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));
    }

    private User saveUser(String name, String email, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("$2a$10$private-password-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private Property saveProperty() {
        Property saved = new Property();
        saved.setSeller(owner);
        saved.setTitle("Original Villa");
        saved.setType(PropertyType.VILLA);
        saved.setCity("Riyadh");
        saved.setDistrict("Al Narjis");
        saved.setArea(new BigDecimal("500"));
        saved.setPrice(new BigDecimal("1500000"));
        saved.setStreetWidth(new BigDecimal("20"));
        saved.setFacade(PropertyFacade.NORTH);
        saved.setPurpose(PropertyPurpose.RESIDENTIAL);
        saved.setDescription("Original description");
        saved.setStatus(PropertyStatus.ACTIVE);
        return propertyRepository.saveAndFlush(saved);
    }

    private String validUpdateRequest(String title) {
        return """
                {
                  "title": "%s",
                  "type": "VILLA",
                  "city": "Riyadh",
                  "district": "Al Yasmin",
                  "area": 550,
                  "price": 1750000,
                  "streetWidth": 25,
                  "facade": "EAST",
                  "purpose": "RESIDENTIAL",
                  "description": "Updated description",
                  "status": "ACTIVE"
                }
                """.formatted(title);
    }

    private String updateRequestWithLocation() {
        return """
                {
                  "title": "Location Updated Villa",
                  "type": "VILLA",
                  "city": "Makkah",
                  "district": "Al Umrah",
                  "googlePlaceId": "ChIJUpdatedPlace456",
                  "formattedAddress": "زهرة العمرة، مكة، السعودية",
                  "neighborhood": "زهرة العمرة",
                  "latitude": 21.5101000,
                  "longitude": 39.9962000,
                  "area": 550,
                  "price": 1750000,
                  "streetWidth": 25,
                  "facade": "EAST",
                  "purpose": "RESIDENTIAL",
                  "description": "Updated location",
                  "status": "ACTIVE"
                }
                """;
    }
}
