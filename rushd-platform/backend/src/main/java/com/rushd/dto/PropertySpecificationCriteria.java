package com.rushd.dto;

import com.fasterxml.jackson.annotation.JsonValue;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rushd.dto.deserialization.PropertySpecificationCriteriaDeserializer;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@JsonDeserialize(using = PropertySpecificationCriteriaDeserializer.class)
public record PropertySpecificationCriteria(@JsonValue Map<String, PropertySpecificationCriterion> values) {
    public static final PropertySpecificationCriteria EMPTY = new PropertySpecificationCriteria(Map.of());
    public PropertySpecificationCriteria {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }
}
