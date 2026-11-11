package com.rushd.dto.deserialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.*;
import com.rushd.dto.*;
import com.rushd.validation.PropertyMatchingFields;
import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Strict binding is local to the newly approved criteria object. */
public class PropertySpecificationCriteriaDeserializer extends JsonDeserializer<PropertySpecificationCriteria> {
    @Override public PropertySpecificationCriteria getNullValue(DeserializationContext context) throws JsonMappingException {
        return context.reportInputMismatch(PropertySpecificationCriteria.class, "specificationCriteria must be an object, not null");
    }
    @Override public PropertySpecificationCriteria deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.START_OBJECT)) return fail(context, "specificationCriteria must be an object");
        Map<String, PropertySpecificationCriterion> values = new LinkedHashMap<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME)) return fail(context, "Invalid criteria object");
            String name = parser.currentName();
            var field = PropertyMatchingFields.ALL.get(name);
            if (field == null || values.containsKey(name)) return fail(context, "Unknown or duplicate criterion");
            if (parser.nextToken() != JsonToken.START_OBJECT) return fail(context, "Criterion must be an object");
            Integer min = null, max = null;
            Object value = null;
            PropertyNeedsRequirement requirement = null;
            Set<String> present = new HashSet<>();
            while (parser.nextToken() != JsonToken.END_OBJECT) {
                if (!parser.hasToken(JsonToken.FIELD_NAME)) return fail(context, "Invalid criterion");
                String key = parser.currentName();
                if (!present.add(key)) return fail(context, "Duplicate criterion member");
                parser.nextToken();
                if (key.equals("requirement")) {
                    if (!parser.hasToken(JsonToken.VALUE_STRING)) return fail(context, "Requirement must be a name");
                    try { requirement = PropertyNeedsRequirement.valueOf(parser.getText()); }
                    catch (IllegalArgumentException invalid) { return fail(context, "Unsupported requirement"); }
                } else if (field.type() == Integer.class && (key.equals("min") || key.equals("max"))) {
                    if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT) && !parser.hasToken(JsonToken.VALUE_NULL)) return fail(context, "Integer bound required");
                    Integer bound = parser.hasToken(JsonToken.VALUE_NULL) ? null : parser.getIntValue();
                    if (key.equals("min")) min = bound; else max = bound;
                } else if (field.type() != Integer.class && key.equals("value")) {
                    if (field.type() == Boolean.class && (parser.hasToken(JsonToken.VALUE_TRUE) || parser.hasToken(JsonToken.VALUE_FALSE))) value = parser.getBooleanValue();
                    else if (field.type().isEnum() && parser.hasToken(JsonToken.VALUE_STRING)) {
                        for (Object constant : field.type().getEnumConstants()) if (((Enum<?>) constant).name().equals(parser.getText())) { value = constant; break; }
                        if (value == null) return fail(context, "Unsupported criterion enum");
                    } else return fail(context, "Invalid criterion value type");
                } else return fail(context, "Unknown or inapplicable criterion member");
            }
            values.put(name, new PropertySpecificationCriterion(min, max, value, requirement));
        }
        return new PropertySpecificationCriteria(values);
    }
    private PropertySpecificationCriteria fail(DeserializationContext context, String message) throws JsonMappingException {
        return context.reportInputMismatch(PropertySpecificationCriteria.class, message);
    }
}
