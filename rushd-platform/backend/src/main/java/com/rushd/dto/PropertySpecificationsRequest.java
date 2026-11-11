package com.rushd.dto;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rushd.dto.deserialization.PropertySpecificationsDeserializer;
import com.rushd.entity.PropertySpecifications;
import com.rushd.validation.ValidPropertySpecifications;
import java.util.Set;

/** Presence is request metadata, never persisted or returned as a property fact. */
@JsonDeserialize(using = PropertySpecificationsDeserializer.class)
@ValidPropertySpecifications
public final class PropertySpecificationsRequest {
    private final PropertySpecifications values;
    private final Set<String> presentFields;

    public PropertySpecificationsRequest(PropertySpecifications values, Set<String> presentFields) {
        this.values = values.copy();
        this.presentFields = Set.copyOf(presentFields);
    }

    public PropertySpecifications values() { return values.copy(); }
    public Set<String> presentFields() { return presentFields; }
}
