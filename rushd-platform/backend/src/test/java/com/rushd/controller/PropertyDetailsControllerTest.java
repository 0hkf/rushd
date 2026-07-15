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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PropertyDetailsControllerTest {

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
    private User admin;
    private Property activeProperty;
    private Property draftProperty;
    private Property inactiveProperty;
    private Property soldProperty;

    @BeforeEach
    void setUp() {
        owner = saveUser("Owner Seller", "owner@rushd.com", Role.SELLER);
        otherSeller = saveUser("Other Seller", "other@rushd.com", Role.SELLER);
        admin = saveUser("Rushd Admin", "admin-details@rushd.com", Role.ADMIN);

        activeProperty = saveProperty("Active Villa", PropertyStatus.ACTIVE);
        draftProperty = saveProperty("Draft Villa", PropertyStatus.DRAFT);
        inactiveProperty = saveProperty("Inactive Villa", PropertyStatus.INACTIVE);
        soldProperty = saveProperty("Sold Villa", PropertyStatus.SOLD);
    }

    @Test
    void existingActivePropertyReturns200() throws Exception {
        activeProperty.setGooglePlaceId("ChIJDetailsPlace123");
        activeProperty.setFormattedAddress("حي الياسمين، الرياض، السعودية");
        activeProperty.setNeighborhood("حي الياسمين");
        activeProperty.setLatitude(new BigDecimal("24.7136000"));
        activeProperty.setLongitude(new BigDecimal("46.6753000"));
        propertyRepository.saveAndFlush(activeProperty);

        getAs(activeProperty.getId(), otherSeller)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(activeProperty.getId()))
                .andExpect(jsonPath("$.seller.id").value(owner.getId()))
                .andExpect(jsonPath("$.seller.name").value("Owner Seller"))
                .andExpect(jsonPath("$.seller.role").value("SELLER"))
                .andExpect(jsonPath("$.title").value("Active Villa"))
                .andExpect(jsonPath("$.type").value("VILLA"))
                .andExpect(jsonPath("$.city").value("Riyadh"))
                .andExpect(jsonPath("$.district").value("Al Narjis"))
                .andExpect(jsonPath("$.googlePlaceId").value("ChIJDetailsPlace123"))
                .andExpect(jsonPath("$.formattedAddress").value("حي الياسمين، الرياض، السعودية"))
                .andExpect(jsonPath("$.neighborhood").value("حي الياسمين"))
                .andExpect(jsonPath("$.latitude").value(24.7136))
                .andExpect(jsonPath("$.longitude").value(46.6753))
                .andExpect(jsonPath("$.area").value(500))
                .andExpect(jsonPath("$.price").value(1500000))
                .andExpect(jsonPath("$.streetWidth").value(20))
                .andExpect(jsonPath("$.facade").value("NORTH"))
                .andExpect(jsonPath("$.purpose").value("RESIDENTIAL"))
                .andExpect(jsonPath("$.description").value("Property details test"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andExpect(jsonPath("$.updatedAt").exists());
    }

    @Test
    void missingPropertyReturns404() throws Exception {
        mockMvc.perform(get("/api/properties/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.path").value("/api/properties/9223372036854775807"));
    }

    @Test
    void activePropertyWorksWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/api/properties/{id}", activeProperty.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void draftPropertyWorksForOwner() throws Exception {
        getAs(draftProperty.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void draftPropertyWorksForAdmin() throws Exception {
        getAs(draftProperty.getId(), admin)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    void draftPropertyIsHiddenFromOtherUsers() throws Exception {
        getAs(draftProperty.getId(), otherSeller)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void draftPropertyIsHiddenFromAnonymousUsers() throws Exception {
        mockMvc.perform(get("/api/properties/{id}", draftProperty.getId()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void inactivePropertyWorksForOwner() throws Exception {
        getAs(inactiveProperty.getId(), owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("INACTIVE"));
    }

    @Test
    void inactivePropertyIsHiddenFromOtherUsers() throws Exception {
        getAs(inactiveProperty.getId(), otherSeller)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void soldPropertyRemainsPublic() throws Exception {
        mockMvc.perform(get("/api/properties/{id}", soldProperty.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SOLD"));
    }

    @Test
    void sellerPasswordIsNeverReturned() throws Exception {
        mockMvc.perform(get("/api/properties/{id}", activeProperty.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.seller.password").doesNotExist())
                .andExpect(jsonPath("$.seller.email").doesNotExist());
    }

    private ResultActions getAs(Long propertyId, User user) throws Exception {
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());
        return mockMvc.perform(get("/api/properties/{id}", propertyId)
                .header("Authorization", "Bearer " + token));
    }

    private User saveUser(String name, String email, Role role) {
        User user = new User();
        user.setName(name);
        user.setEmail(email);
        user.setPassword("$2a$10$private-password-hash");
        user.setRole(role);
        return userRepository.saveAndFlush(user);
    }

    private Property saveProperty(String title, PropertyStatus status) {
        Property property = new Property();
        property.setSeller(owner);
        property.setTitle(title);
        property.setType(PropertyType.VILLA);
        property.setCity("Riyadh");
        property.setDistrict("Al Narjis");
        property.setArea(new BigDecimal("500"));
        property.setPrice(new BigDecimal("1500000"));
        property.setStreetWidth(new BigDecimal("20"));
        property.setFacade(PropertyFacade.NORTH);
        property.setPurpose(PropertyPurpose.RESIDENTIAL);
        property.setDescription("Property details test");
        property.setStatus(status);
        return propertyRepository.saveAndFlush(property);
    }
}
