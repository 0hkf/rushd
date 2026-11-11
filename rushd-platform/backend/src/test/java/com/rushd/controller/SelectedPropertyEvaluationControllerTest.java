package com.rushd.controller;

import com.rushd.entity.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.rushd.repository.PropertyRepository;
import com.rushd.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SelectedPropertyEvaluationControllerTest {
    @Autowired MockMvc mvc;
    @Autowired PropertyRepository properties;
    @Autowired UserRepository users;
    @Autowired ObjectMapper mapper;
    @Autowired EntityManager entities;
    private Property property;
    private String path;
    private static final String BODY = "{\"listingType\":\"SALE\",\"type\":\"LAND\",\"maxBudget\":\"9007199254740993.01\"}";
    @BeforeEach void setup() {
        User user = new User(); user.setName("Evaluation test"); user.setEmail("evaluation-test@example.invalid");
        user.setPassword("test-hash"); user.setRole(Role.ADMIN); users.saveAndFlush(user);
        property = new Property(); property.setStatus(PropertyStatus.ACTIVE); property.setPublisher(user);
        property.setTitle("Test property"); property.setDistrict("النوارية");
        property.setArea(new BigDecimal("100.00")); property.setPurpose(PropertyPurpose.RESIDENTIAL);
        property.setType(PropertyType.LAND); property.setListingType(ListingType.SALE);
        property.setCity("مكة"); property.setPrice(new BigDecimal("9007199254740993.01"));
        properties.saveAndFlush(property);
        path = "/api/property-needs/evaluations/" + property.getId();
    }
    @Test @WithMockUser void validRequestReturnsExactSnapshot() throws Exception {
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk()).andExpect(jsonPath("$.propertyId").value(property.getId().toString()))
                .andExpect(jsonPath("$.classification").value("BASIC_MATCH"))
                .andExpect(jsonPath("$.needs.maxBudget").value("9007199254740993.01"))
                .andExpect(jsonPath("$.rulesVersion").value("selected-property-evaluation-v1"));
    }
    @Test @WithMockUser void areaEvidenceSerializesBoundsAndBasis() throws Exception {
        String body = "{\"listingType\":\"SALE\",\"type\":\"LAND\",\"maxBudget\":\"9007199254740993.01\",\"minArea\":\"100.00\"}";
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.evidence[4].id").value("area"))
                .andExpect(jsonPath("$.evidence[4].requested.min").value("100.00"))
                .andExpect(jsonPath("$.evidence[4].requested.basis").value("ADVERTISED"))
                .andExpect(jsonPath("$.evidence[4].actual").value("100.00"));
    }
    @Test @WithMockUser void freshDatabaseReadReflectsPriceAndPublicationChanges() throws Exception {
        Long id = property.getId();
        entities.clear();
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("BASIC_MATCH"))
                .andExpect(jsonPath("$.evidence[3].actual").value("9007199254740993.01"));

        entities.clear();
        Property updated = properties.findById(id).orElseThrow();
        updated.setPrice(new BigDecimal("9007199254740993.02"));
        properties.saveAndFlush(updated);
        entities.clear();
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classification").value("NOT_SUITABLE"))
                .andExpect(jsonPath("$.evidence[3].actual").value("9007199254740993.02"));

        entities.clear();
        Property hidden = properties.findById(id).orElseThrow();
        hidden.setStatus(PropertyStatus.INACTIVE);
        properties.saveAndFlush(hidden);
        entities.clear();
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());
    }
    @Test @WithMockUser(roles = "ADMIN") void soldAndMissingAreIndistinguishableForAdmin() throws Exception {
        property.setStatus(PropertyStatus.SOLD);
        properties.flush();
        String sold = mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        properties.delete(property); properties.flush();
        String missing = mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString();
        ObjectNode soldJson = (ObjectNode) mapper.readTree(sold);
        ObjectNode missingJson = (ObjectNode) mapper.readTree(missing);
        soldJson.remove("timestamp"); missingJson.remove("timestamp");
        org.junit.jupiter.api.Assertions.assertEquals(soldJson, missingJson);
    }
    @Test @WithMockUser void invalidNeedsAreRejectedBeforeRepositoryAccess() throws Exception {
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"type\":\"LAND\"}"))
                .andExpect(status().isBadRequest());
    }
    @Test @WithMockUser void csrfIsRequired() throws Exception {
        mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
        mvc.perform(post(path).with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isForbidden());
    }
    @Test void unauthenticatedIsRejected() throws Exception {
        mvc.perform(post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)).andExpect(status().isUnauthorized());
    }
}
