package com.rushd.validation;

import com.rushd.dto.CreatePropertyRequest;
import com.rushd.dto.UpdatePropertyRequest;
import com.rushd.entity.Property;
import jakarta.persistence.Column;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeDecimalPrecisionValidatorTest {
    @ParameterizedTest
    @CsvSource({
            "price, 99999999999999999.99, true",
            "price, 100000000000000000, false",
            "price, 1.000, false",
            "price, 1E+16, true",
            "area, 9999999999.99, true",
            "area, 10000000000, false",
            "streetWidth, 99999999.99, true",
            "streetWidth, 100000000, false",
            "latitude, -90.0000000, true",
            "longitude, 180.0000000, true",
            "latitude, -0.0000001, true",
            "latitude, 0.00000001, false",
            "longitude, 39.00000000, false",
            "latitude, 0E+2147483647, false",
            "latitude, 0E-2147483647, false",
            "longitude, 1E+2147483647, false",
            "longitude, 1E-2147483647, false",
            "price, 1E+2147483647, false",
            "price, 0E+2147483647, false"
    })
    void decimalShapeIsCheckedWithoutOverflowOrExpandingTheValue(String field, String value, boolean expected)
            throws Exception {
        SafeDecimalPrecisionValidator validator = validator(CreatePropertyRequest.class, field);
        assertEquals(expected, validator.isValid(new BigDecimal(value), null));
    }

    @Test
    void createAndUpdateShapesAgreeWithJpaAndAllowNullForSeparateRequiredValidation() throws Exception {
        for (Class<?> request : new Class<?>[]{CreatePropertyRequest.class, UpdatePropertyRequest.class}) {
            for (String field : new String[]{"price", "area", "streetWidth", "latitude", "longitude"}) {
                SafeDecimalPrecision shape = request.getDeclaredField(field).getAnnotation(SafeDecimalPrecision.class);
                Column column = Property.class.getDeclaredField(field).getAnnotation(Column.class);
                assertNotNull(shape);
                assertEquals(column.precision() - column.scale(), shape.integer());
                assertEquals(column.scale(), shape.fraction());
                assertTrue(validator(request, field).isValid(null, null));
            }
        }
    }

    private SafeDecimalPrecisionValidator validator(Class<?> request, String field) throws Exception {
        SafeDecimalPrecisionValidator validator = new SafeDecimalPrecisionValidator();
        validator.initialize(request.getDeclaredField(field).getAnnotation(SafeDecimalPrecision.class));
        return validator;
    }
}
