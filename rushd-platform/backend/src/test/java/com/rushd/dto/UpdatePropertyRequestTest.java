package com.rushd.dto;

import com.rushd.entity.PropertyPurpose;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.PropertyType;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdatePropertyRequestTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void validRequestWithoutOptionalFieldsPassesValidation() {
        assertTrue(validator.validate(validRequest()).isEmpty());
    }

    @Test
    void requiredAndPositiveFieldsAreValidated() {
        UpdatePropertyRequest request = validRequest();
        request.setTitle("   ");
        request.setType(null);
        request.setCity("   ");
        request.setDistrict("   ");
        request.setArea(BigDecimal.ZERO);
        request.setPrice(new BigDecimal("-1"));
        request.setStreetWidth(BigDecimal.ZERO);
        request.setPurpose(null);
        request.setStatus(null);

        assertEquals(
                Set.of("title", "type", "city", "district", "area", "price",
                        "streetWidth", "purpose", "status"),
                violatedFields(request));
    }

    @Test
    void textLengthsAndCoordinatesAreValidated() {
        UpdatePropertyRequest request = validRequest();
        request.setTitle("x".repeat(151));
        request.setCity("x".repeat(101));
        request.setDistrict("x".repeat(101));
        request.setGooglePlaceId("x".repeat(256));
        request.setFormattedAddress("x".repeat(501));
        request.setNeighborhood("x".repeat(151));
        request.setDescription("x".repeat(3001));
        request.setLatitude(new BigDecimal("90.0000001"));
        request.setLongitude(new BigDecimal("-180.0000001"));

        assertEquals(
                Set.of("title", "city", "district", "googlePlaceId", "formattedAddress",
                        "neighborhood", "description", "latitude", "longitude"),
                violatedFields(request));
    }

    private Set<String> violatedFields(UpdatePropertyRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    private UpdatePropertyRequest validRequest() {
        UpdatePropertyRequest request = new UpdatePropertyRequest();
        request.setTitle("Updated villa");
        request.setType(PropertyType.VILLA);
        request.setCity("Riyadh");
        request.setDistrict("Al Yasmin");
        request.setArea(new BigDecimal("500"));
        request.setPrice(new BigDecimal("1500000"));
        request.setPurpose(PropertyPurpose.RESIDENTIAL);
        request.setStatus(PropertyStatus.ACTIVE);
        return request;
    }
}
