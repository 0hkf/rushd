package com.rushd.drafts;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DraftMergeTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @Test void coupledFieldsRequireOneExplicitChoiceAndNeverCoerceDecimalText() throws Exception {
        var local = mapper.readTree("""
                {"values":{"price":"٩٠٠٧١٩٩٢٥٤٧٤٠٩٩٣٫٠١","listingType":"RENT","rentalPeriod":"MONTHLY","area":"100"},
                 "specifications":{"estimatedAgeYears":"20","ageAsOfYear":"2026","landArea":"100","primaryAreaSource":"LAND"}}
                """);
        var remote = mapper.readTree("""
                {"values":{"price":"100","listingType":"SALE","rentalPeriod":"","area":"200"},
                 "specifications":{"estimatedAgeYears":"10","ageAsOfYear":"2025","landArea":"200","primaryAreaSource":"LAND"}}
                """);
        var sources = Map.of("LOCAL", local, "SERVER", remote);
        var merger = new DraftMerge(mapper);
        var choices = mapper.createObjectNode().put("offer", "LOCAL").put("areas", "SERVER").put("age", "LOCAL");
        var merged = merger.merge(sources, choices);
        assertEquals("٩٠٠٧١٩٩٢٥٤٧٤٠٩٩٣٫٠١", merged.path("values").path("price").asText());
        assertEquals("MONTHLY", merged.path("values").path("rentalPeriod").asText());
        assertEquals("200", merged.path("values").path("area").asText());
        assertEquals("2026", merged.path("specifications").path("ageAsOfYear").asText());
        choices.remove("age"); assertThrows(DraftFailure.class, () -> merger.merge(sources, choices));
        choices.putNull("age"); assertThrows(DraftFailure.class, () -> merger.merge(sources, choices));
        choices.put("age", "UNKNOWN"); assertThrows(DraftFailure.class, () -> merger.merge(sources, choices));
    }

    @Test void differentTypesKeepAllSpecificationsAndAreaInOneReviewedGroup() throws Exception {
        var local = mapper.readTree("{\"values\":{\"type\":\"LAND\",\"area\":\"100\"},\"specifications\":{\"landArea\":\"100\"}}");
        var remote = mapper.readTree("{\"values\":{\"type\":\"APARTMENT\",\"area\":\"120\"},\"specifications\":{\"bedrooms\":\"2\"}}");
        var merger = new DraftMerge(mapper); var sources = Map.of("LOCAL", local, "SERVER", remote);
        var merged = merger.merge(sources, mapper.createObjectNode().put("type-context", "LOCAL"));
        assertEquals(local, merged);
    }

    @Test void anErrorWithoutAFieldValueIsNotSilentlyDropped() throws Exception {
        var local = mapper.readTree("{\"values\":{},\"specifications\":{},\"sourceErrors\":{\"price\":\"تعذرت القراءة\"}}");
        var remote = mapper.readTree("{\"values\":{},\"specifications\":{}}");
        var merged = new DraftMerge(mapper).merge(Map.of("LOCAL", local, "SERVER", remote), mapper.createObjectNode().put("offer", "LOCAL"));
        assertEquals("تعذرت القراءة", merged.path("sourceErrors").path("price").asText());
        assertFalse(merged.path("values").has("price"));
    }
}
