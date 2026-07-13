package com.rushd.mapper;

import com.rushd.dto.PropertyResponse;
import com.rushd.entity.Property;

public final class PropertyMapper {

    private PropertyMapper() {
    }

    public static PropertyResponse toResponse(Property property) {
        return new PropertyResponse(
                property.getId(),
                property.getSeller().getId(),
                property.getSeller().getName(),
                property.getTitle(),
                property.getType(),
                property.getCity(),
                property.getDistrict(),
                property.getArea(),
                property.getPrice(),
                property.getStreetWidth(),
                property.getFacade(),
                property.getPurpose(),
                property.getDescription(),
                property.getStatus(),
                property.getCreatedAt(),
                property.getUpdatedAt()
        );
    }
}
