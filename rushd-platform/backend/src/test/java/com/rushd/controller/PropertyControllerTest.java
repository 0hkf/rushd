package com.rushd.controller;

import com.rushd.entity.Property;
import com.rushd.entity.Role;
import com.rushd.entity.User;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import com.rushd.service.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PropertyControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @MockBean
    private UserRepository userRepository;

    @MockBean
    private PropertyRepository propertyRepository;

    @BeforeEach
    void setUp() {
        when(propertyRepository.save(any(Property.class))).thenAnswer(invocation -> {
            Property property = invocation.getArgument(0);
            property.setId(99L);
            return property;
        });
    }

    @Test
    void sellerSuccessfullyCreatesProperty() throws Exception {
        User seller = buildUser(7L, "seller@rushd.com", "Rushd Seller", Role.SELLER);

        performAs(seller, validRequest())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(99))
                .andExpect(jsonPath("$.sellerId").value(7))
                .andExpect(jsonPath("$.sellerName").value("Rushd Seller"))
                .andExpect(jsonPath("$.title").value("Residential Land in Riyadh"))
                .andExpect(jsonPath("$.facade").value("UNKNOWN"))
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.seller").doesNotExist());
    }

    @Test
    void missingTokenReturns401() throws Exception {
        mockMvc.perform(post("/api/properties")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"));

        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    void buyerTokenReturns403() throws Exception {
        User buyer = buildUser(8L, "buyer@rushd.com", "Rushd Buyer", Role.BUYER);

        performAs(buyer, validRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    void adminTokenReturns403() throws Exception {
        User admin = buildUser(9L, "admin@rushd.com", "Rushd Admin", Role.ADMIN);

        performAs(admin, validRequest())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));

        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    void invalidAreaReturns400() throws Exception {
        User seller = buildUser(7L, "seller@rushd.com", "Rushd Seller", Role.SELLER);
        String request = validRequest().replace("\"area\": 450.50", "\"area\": 0");

        performAs(seller, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.area").exists());

        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    void invalidPriceReturns400() throws Exception {
        User seller = buildUser(7L, "seller@rushd.com", "Rushd Seller", Role.SELLER);
        String request = validRequest().replace("\"price\": 1250000", "\"price\": -1");

        performAs(seller, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.price").exists());

        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    void blankTitleReturns400() throws Exception {
        User seller = buildUser(7L, "seller@rushd.com", "Rushd Seller", Role.SELLER);
        String request = validRequest().replace(
                "\"title\": \"Residential Land in Riyadh\"",
                "\"title\": \"   \"");

        performAs(seller, request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.title").exists());

        verify(propertyRepository, never()).save(any(Property.class));
    }

    @Test
    void suppliedSellerIdCannotOverrideAuthenticatedSeller() throws Exception {
        User seller = buildUser(7L, "seller@rushd.com", "Rushd Seller", Role.SELLER);
        String request = validRequest().replace("{", "{\"sellerId\": 999,");

        performAs(seller, request)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sellerId").value(7));

        ArgumentCaptor<Property> propertyCaptor = ArgumentCaptor.forClass(Property.class);
        verify(propertyRepository).save(propertyCaptor.capture());
        assertEquals(7L, propertyCaptor.getValue().getSeller().getId());
    }

    private ResultActions performAs(User user, String request) throws Exception {
        when(userRepository.findByEmail(user.getEmail())).thenReturn(Optional.of(user));
        String token = jwtService.generateToken(user.getEmail(), user.getRole().name());

        return mockMvc.perform(post("/api/properties")
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(request));
    }

    private User buildUser(Long id, String email, String name, Role role) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setName(name);
        user.setPassword("$2a$10$irrelevant-hash");
        user.setRole(role);
        return user;
    }

    private String validRequest() {
        return """
                {
                  "title": "Residential Land in Riyadh",
                  "type": "LAND",
                  "city": "Riyadh",
                  "district": "Al Narjis",
                  "area": 450.50,
                  "price": 1250000,
                  "streetWidth": 20,
                  "purpose": "RESIDENTIAL",
                  "description": "Corner residential land"
                }
                """;
    }
}
