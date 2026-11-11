package com.rushd.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.Role;
import com.rushd.entity.User;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import com.rushd.service.AuthCookieService;
import com.rushd.service.JwtService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class PropertyListingStatusValidationControllerTest {
    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper mapper;
    @Autowired private JwtService jwt;
    @MockBean private UserRepository users;
    @MockBean private PropertyRepository properties;
    private Cookie adminAccess;

    @BeforeEach
    void setup() {
        User admin = new User();
        admin.setId(1L);
        admin.setName("Listing status validation");
        admin.setEmail("listing-status-validation@example.com");
        admin.setPassword("unused-hash");
        admin.setRole(Role.ADMIN);
        when(users.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        adminAccess = new Cookie(AuthCookieService.ACCESS, jwt.generateAccessToken(admin.getEmail()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"MONTHLY", "YEARLY"})
    void soldRentalIsRejectedBeforeAnyPropertyRepositoryAccess(String period) throws Exception {
        Map<String, Object> body = validBody();
        body.put("listingType", "RENT");
        body.put("rentalPeriod", period);
        body.put("status", "SOLD");
        body.put("listingStatusValid", true);
        for (var request : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{
                post("/api/properties"), put("/api/properties/91")}) {
            mvc.perform(request.with(csrf()).cookie(adminAccess)
                            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message.listingStatusValid")
                            .value("حالة مباع لعروض البيع فقط. اختر حالة مناسبة للإيجار قبل الحفظ."));
        }
        verifyNoInteractions(properties);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SALE", "RENT"})
    void nullUpdateStatusIsStillRejectedBeforeRepositoryAccess(String listingType) throws Exception {
        Map<String, Object> body = validBody();
        body.put("listingType", listingType);
        body.put("rentalPeriod", listingType.equals("RENT") ? "MONTHLY" : null);
        body.put("status", null);
        mvc.perform(put("/api/properties/91").with(csrf()).cookie(adminAccess)
                        .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsString(body)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message.status").exists());
        verifyNoInteractions(properties);
    }

    private Map<String, Object> validBody() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", "Listing status property");
        body.put("type", "APARTMENT");
        body.put("city", "مكة المكرمة");
        body.put("district", "النوارية");
        body.put("area", "150.25");
        body.put("price", "500000.25");
        body.put("purpose", "RESIDENTIAL");
        body.put("status", "ACTIVE");
        return body;
    }
}
