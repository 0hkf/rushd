package com.rushd.drafts;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.entity.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PropertyDraftFingerprintTest {
    @Test void coversEditableFactsAndSpecificationsNotPublisher() {
        User owner = new User(); owner.setId(1L); owner.setName("first"); owner.setRole(Role.ADMIN);
        Property property = new Property(); property.setId(10L); property.setPublisher(owner);
        property.setTitle("عقار"); property.setType(PropertyType.APARTMENT); property.setCity("مكة");
        property.setDistrict("النوارية"); property.setArea(new BigDecimal("100.00"));
        property.setPrice(new BigDecimal("9007199254740993.01")); property.setPurpose(PropertyPurpose.RESIDENTIAL);
        PropertySpecifications specs = new PropertySpecifications(); specs.setHasElevator(false); property.setSpecifications(specs);
        var fingerprint = new PropertyDraftFingerprint(new ObjectMapper().findAndRegisterModules());
        String original = fingerprint.of(property);
        assertEquals(64, original.length());
        assertEquals(original, fingerprint.of(property));
        owner.setName("different");
        assertEquals(original, fingerprint.of(property));
        property.setPrice(new BigDecimal("9007199254740993.02"));
        assertNotEquals(original, fingerprint.of(property));
        property.setPrice(new BigDecimal("9007199254740993.01"));
        specs.setHasElevator(true);
        assertNotEquals(original, fingerprint.of(property));
        specs.setHasElevator(null);
        assertNotEquals(original, fingerprint.of(property));
    }
}
