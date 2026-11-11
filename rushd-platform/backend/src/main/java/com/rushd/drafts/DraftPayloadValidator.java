package com.rushd.drafts;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

/** Validation for an incomplete editor snapshot, not validation for publishing a property. */
public final class DraftPayloadValidator {
    public static final int MAX_PAYLOAD_BYTES = 64 * 1024;
    public static final int MAX_FIELD_BYTES = 16 * 1024;

    private static final Set<String> ROOT_FIELDS = Set.of("values", "specifications", "sourceErrors", "specificationSourceErrors", "typeDrafts", "scopes");
    private static final Set<String> TYPES = Set.of("LAND", "APARTMENT", "VILLA", "BUILDING", "COMMERCIAL", "FARM", "OTHER");
    private static final Set<String> PROPERTY_FIELDS = Set.of(
            "title", "type", "city", "district", "area", "price", "listingType", "rentalPeriod",
            "streetWidth", "facade", "purpose", "description", "status", "formattedAddress",
            "neighborhood", "latitude", "longitude", "googlePlaceId");
    private static final Set<String> SPECIFICATION_FIELDS = Set.of(
            "primaryAreaSource", "landArea", "unitNetArea", "unitGrossArea", "totalBuiltArea",
            "bedrooms", "bathrooms", "livingRooms", "floorNumber", "floorCount", "totalUnits",
            "residentialUnits", "commercialUnits", "parkingSpaces", "estimatedAgeYears", "ageAsOfYear",
            "condition", "finishing", "furnishing", "hasElevator", "hasAirConditioning", "hasFittedKitchen",
            "hasMaidRoom", "hasDriverRoom", "hasPool", "hasYard", "hasBalcony", "hasPrivateRoof",
            "electricityConnected", "waterConnected", "sewerConnected", "hasIrrigation", "wellCount",
            "waterSource", "commercialScope", "commercialSubtype", "otherTypeDescription");

    private final JsonFactory factory = JsonFactory.builder()
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build();
    private final ObjectMapper mapper = new ObjectMapper(factory);

    /** Returns the original text unchanged; the caller must not interpret this as publish validation. */
    public String validate(String raw) {
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > MAX_PAYLOAD_BYTES) throw invalid();
        try (JsonParser parser = factory.createParser(raw)) {
            JsonNode root = mapper.readTree(parser);
            if (root == null || !root.isObject() || parser.nextToken() != null) throw invalid();
            var fields = root.fieldNames();
            while (fields.hasNext()) if (!ROOT_FIELDS.contains(fields.next())) throw invalid();
            if (!root.has("values") || !root.has("specifications")) throw invalid();
            validateMap(root.get("values"), PROPERTY_FIELDS);
            validateMap(root.get("specifications"), SPECIFICATION_FIELDS);
            if (root.has("sourceErrors")) validateMap(root.get("sourceErrors"), PROPERTY_FIELDS);
            if (root.has("specificationSourceErrors")) validateMap(root.get("specificationSourceErrors"), SPECIFICATION_FIELDS);
            if (root.has("typeDrafts")) validateTypeDrafts(root.get("typeDrafts"));
            if (root.has("scopes")) validateScopes(root.get("scopes"));
            return raw;
        } catch (IOException e) {
            throw invalid();
        }
    }

    private static void validateTypeDrafts(JsonNode drafts) {
        if (drafts == null || !drafts.isObject() || drafts.size() > 16) throw invalid();
        var entries = drafts.fields();
        while (entries.hasNext()) {
            var entry = entries.next();
            String[] parts = entry.getKey().split(":", -1);
            if (parts.length != 2 || !TYPES.contains(parts[0])
                    || !(parts[1].isEmpty() || (parts[0].equals("COMMERCIAL")
                    && Set.of("UNIT", "WHOLE_BUILDING").contains(parts[1])))) throw invalid();
            JsonNode draft = entry.getValue();
            if (draft == null || !draft.isObject() || draft.size() != 3
                    || !draft.has("values") || !draft.has("area") || !draft.has("sourceErrors")) throw invalid();
            validateMap(draft.get("values"), SPECIFICATION_FIELDS);
            validateMap(draft.get("sourceErrors"), SPECIFICATION_FIELDS);
            JsonNode area = draft.get("area");
            if (!area.isTextual() || area.textValue().getBytes(StandardCharsets.UTF_8).length > MAX_FIELD_BYTES) throw invalid();
        }
    }

    private static void validateScopes(JsonNode scopes) {
        if (scopes == null || !scopes.isObject() || scopes.size() > TYPES.size()) throw invalid();
        var entries = scopes.fields();
        while (entries.hasNext()) {
            var entry = entries.next();
            if (!TYPES.contains(entry.getKey()) || !entry.getValue().isTextual()
                    || !Set.of("", "UNIT", "WHOLE_BUILDING").contains(entry.getValue().textValue())) throw invalid();
        }
    }

    private static void validateMap(JsonNode object, Set<String> allowed) {
        if (object == null || !object.isObject()) throw invalid();
        var fields = object.fields();
        while (fields.hasNext()) {
            var field = fields.next();
            if (!allowed.contains(field.getKey()) || !field.getValue().isTextual()
                    || field.getValue().textValue().getBytes(StandardCharsets.UTF_8).length > MAX_FIELD_BYTES) throw invalid();
        }
    }

    private static IllegalArgumentException invalid() { return new IllegalArgumentException("Invalid draft payload"); }
}
