package com.rushd.drafts;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DraftPayloadValidatorTest {
    private final DraftPayloadValidator validator = new DraftPayloadValidator();

    @Test void preservesIncompleteAndArabicValuesWithoutNumericCoercion() {
        String raw = "{\"values\":{\"price\":\"٩٠٠٧١٩٩٢٥٤٧٤٠٩٩٣٫٠١\",\"latitude\":\"-\"},"
                + "\"specifications\":{\"hasElevator\":\"false\",\"bedrooms\":\"0\"}}";
        assertEquals(raw, validator.validate(raw));
    }

    @Test void acceptsSourceErrorsForKnownFields() {
        String raw = "{\"values\":{},\"specifications\":{},\"sourceErrors\":{\"area\":\"تعذر القراءة\"},"
                + "\"specificationSourceErrors\":{\"landArea\":\"تعذر القراءة\"}}";
        assertEquals(raw, validator.validate(raw));
    }

    @Test void preservesPerTypeInputAndRejectsUnrecognizedContext() {
        String raw = "{\"values\":{},\"specifications\":{},\"typeDrafts\":{\"LAND:\":{"
                + "\"values\":{\"landArea\":\"١٢٫\"},\"area\":\"١٢٫\",\"sourceErrors\":{}}},\"scopes\":{\"LAND\":\"\"}}";
        assertEquals(raw, validator.validate(raw));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(raw.replace("LAND:", "LAND:UNIT")));
    }

    @Test void rejectsUnlistedCredentialsNestedObjectsAndNumericCoercion() {
        for (String raw : new String[]{
                "{\"values\":{\"password\":\"x\"},\"specifications\":{}}",
                "{\"values\":{},\"specifications\":{},\"ownerId\":\"1\"}",
                "{\"values\":{\"price\":9007199254740993},\"specifications\":{}}",
                "{\"values\":{\"title\":{}},\"specifications\":{}}",
                "{\"values\":{},\"specifications\":{\"hasPool\":false}}"}) {
            assertThrows(IllegalArgumentException.class, () -> validator.validate(raw));
        }
    }

    @Test void rejectsDuplicateKeysTrailingJsonAndMalformedPayloads() {
        for (String raw : new String[]{"{}", "null", "[]", "{\"values\":{},\"specifications\":{}} {}",
                "{\"values\":{\"title\":\"one\",\"title\":\"two\"},\"specifications\":{}}", "{"}) {
            assertThrows(IllegalArgumentException.class, () -> validator.validate(raw));
        }
        assertThrows(IllegalArgumentException.class, () -> validator.validate(null));
    }

    @Test void appliesByteLimitsWithoutTruncating() {
        String atLimit = "ع".repeat(DraftPayloadValidator.MAX_FIELD_BYTES / 2);
        String raw = "{\"values\":{\"description\":\"" + atLimit + "\"},\"specifications\":{}}";
        assertEquals(raw, validator.validate(raw));
        String over = "{\"values\":{\"description\":\"" + atLimit + "ع\"},\"specifications\":{}}";
        assertThrows(IllegalArgumentException.class, () -> validator.validate(over));
        assertThrows(IllegalArgumentException.class, () -> validator.validate(" ".repeat(DraftPayloadValidator.MAX_PAYLOAD_BYTES + 1)));
    }
}
