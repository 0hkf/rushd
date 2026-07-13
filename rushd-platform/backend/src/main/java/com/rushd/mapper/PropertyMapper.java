package com.rushd.mapper;

import com.rushd.dto.PropertyResponse;
import com.rushd.dto.SellerSummaryResponse;
import com.rushd.entity.Property;

public final class PropertyMapper {

    private PropertyMapper() {
    }

    public static PropertyResponse toResponse(Property property) {
        return toResponse(property, null);
    }

    public static PropertyResponse toDetailsResponse(Property property) {
        SellerSummaryResponse seller = new SellerSummaryResponse(
                property.getSeller().getId(),
                property.getSeller().getName(),
                property.getSeller().getRole()
        );
        return toResponse(property, seller);
    }

    private static PropertyResponse toResponse(
            Property property,
            SellerSummaryResponse seller) {
        return new PropertyResponse(
                property.getId(),
                seller,
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
