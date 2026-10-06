package com.rushd.dto.deserialization;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.BeanProperty;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.deser.ContextualDeserializer;

import java.io.IOException;

/** Opt-in enum-name binding: never interpret JSON numbers as enum ordinals. */
public class StrictStringEnumDeserializer extends JsonDeserializer<Object> implements ContextualDeserializer {
    private final Class<?> enumType;

    public StrictStringEnumDeserializer() {
        this.enumType = null;
    }

    private StrictStringEnumDeserializer(Class<?> enumType) {
        this.enumType = enumType;
    }

    @Override
    public JsonDeserializer<?> createContextual(DeserializationContext context, BeanProperty property)
            throws JsonMappingException {
        JavaType type = property == null ? context.getContextualType() : property.getType();
        if (type == null || !type.getRawClass().isEnum()) {
            return context.reportBadDefinition(type, "StrictStringEnumDeserializer requires an enum field");
        }
        return new StrictStringEnumDeserializer(type.getRawClass());
    }

    @Override
    public Object deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        if (!parser.hasToken(JsonToken.VALUE_STRING)) {
            return context.handleUnexpectedToken(enumType, parser);
        }
        String value = parser.getText();
        for (Object constant : enumType.getEnumConstants()) {
            if (((Enum<?>) constant).name().equals(value)) {
                return constant;
            }
        }
        return context.handleWeirdStringValue(enumType, value, "Expected a supported enum name");
    }
}
