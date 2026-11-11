package com.rushd.dto.deserialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.rushd.dto.PropertySpecificationsRequest;
import com.rushd.entity.PropertySpecifications;
import com.rushd.validation.PropertySpecificationFields;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Set;

/** Strict only for this new object; never changes legacy JSON coercion settings. */
public class PropertySpecificationsDeserializer extends JsonDeserializer<PropertySpecificationsRequest> {
    @Override
    public PropertySpecificationsRequest getNullValue(DeserializationContext context) throws JsonMappingException {
        return context.reportInputMismatch(PropertySpecificationsRequest.class, "specifications must be an object, not null");
    }

    @Override
    public PropertySpecificationsRequest deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.START_OBJECT)) {
            return context.reportInputMismatch(PropertySpecificationsRequest.class, "specifications must be an object");
        }
        PropertySpecifications values = new PropertySpecifications();
        Set<String> present = new LinkedHashSet<>();
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            if (!parser.hasToken(JsonToken.FIELD_NAME)) {
                return context.reportInputMismatch(PropertySpecificationsRequest.class, "Invalid specifications object");
            }
            String name = parser.currentName();
            var field = PropertySpecificationFields.BY_NAME.get(name);
            if (field == null || !present.add(name)) {
                return context.reportInputMismatch(PropertySpecificationsRequest.class, "Unknown or duplicate specification: %s", name);
            }
            parser.nextToken();
            bind(field, values, parser, context);
        }
        return new PropertySpecificationsRequest(values, present);
    }

    private <T> void bind(PropertySpecificationFields.Field<T> field, PropertySpecifications values,
                          JsonParser parser, DeserializationContext context) throws IOException {
        Object value = null;
        if (!parser.hasToken(JsonToken.VALUE_NULL)) {
            Class<T> type = field.type();
            if (type == Boolean.class && (parser.hasToken(JsonToken.VALUE_TRUE) || parser.hasToken(JsonToken.VALUE_FALSE))) {
                value = parser.getBooleanValue();
            } else if (type == Integer.class && parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                value = parser.getIntValue(); // Overflow is a binding error, never truncation.
            } else if (parser.hasToken(JsonToken.VALUE_STRING)) {
                String text = parser.getText();
                if (type == BigDecimal.class && text.length() <= 64 && text.matches("-?[0-9]+(?:\\.[0-9]+)?")) {
                    value = new BigDecimal(text);
                } else if (type == String.class) {
                    value = text.trim().isEmpty() ? null : text.trim();
                } else if (type.isEnum()) {
                    for (T constant : type.getEnumConstants()) {
                        if (((Enum<?>) constant).name().equals(text)) { value = constant; break; }
                    }
                    if (value == null) context.reportInputMismatch(type, "Unsupported enum name for %s", field.name());
                } else {
                    context.reportInputMismatch(type, "Invalid value for %s", field.name());
                }
            } else {
                context.reportInputMismatch(type, "Invalid JSON type for %s", field.name());
            }
        }
        field.write().accept(values, field.type().cast(value));
    }
}
