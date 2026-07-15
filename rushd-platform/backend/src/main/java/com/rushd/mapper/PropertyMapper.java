package com.rushd.mapper;

import com.rushd.dto.CreatePropertyRequest;
import com.rushd.dto.PropertyResponse;
import com.rushd.dto.SellerSummaryResponse;
import com.rushd.dto.UpdatePropertyRequest;
import com.rushd.entity.Property;
import com.rushd.entity.PropertyFacade;
import com.rushd.entity.PropertyPurpose;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.PropertyType;

import java.math.BigDecimal;

public final class PropertyMapper {

    private PropertyMapper() {
    }

    public static PropertyResponse toResponse(Property property) {
        return toResponse(property, null);
    }

    public static void applyCreateRequest(Property property, CreatePropertyRequest request) {
        applyEditableFields(
                property,
                request.getTitle(),
                request.getType(),
                request.getCity(),
                request.getDistrict(),
                request.getGooglePlaceId(),
                request.getFormattedAddress(),
                request.getNeighborhood(),
                request.getLatitude(),
                request.getLongitude(),
                request.getArea(),
                request.getPrice(),
                request.getStreetWidth(),
                request.getFacade() == null ? PropertyFacade.UNKNOWN : request.getFacade(),
                request.getPurpose(),
                request.getDescription(),
                request.getStatus() == null ? PropertyStatus.ACTIVE : request.getStatus()
        );
    }

    public static void applyUpdateRequest(Property property, UpdatePropertyRequest request) {
        applyEditableFields(
                property,
                request.getTitle(),
                request.getType(),
                request.getCity(),
                request.getDistrict(),
                request.getGooglePlaceId(),
                request.getFormattedAddress(),
                request.getNeighborhood(),
                request.getLatitude(),
                request.getLongitude(),
                request.getArea(),
                request.getPrice(),
                request.getStreetWidth(),
                request.getFacade(),
                request.getPurpose(),
                request.getDescription(),
                request.getStatus()
        );
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
                property.getGooglePlaceId(),
                property.getFormattedAddress(),
                property.getNeighborhood(),
                property.getLatitude(),
                property.getLongitude(),
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

    private static void applyEditableFields(
            Property property,
            String title,
            PropertyType type,
            String city,
            String district,
            String googlePlaceId,
            String formattedAddress,
            String neighborhood,
            BigDecimal latitude,
            BigDecimal longitude,
            BigDecimal area,
            BigDecimal price,
            BigDecimal streetWidth,
            PropertyFacade facade,
            PropertyPurpose purpose,
            String description,
            PropertyStatus status) {
        property.setTitle(title.trim());
        property.setType(type);
        property.setCity(city.trim());
        property.setDistrict(district.trim());
        property.setGooglePlaceId(googlePlaceId);
        property.setFormattedAddress(formattedAddress);
        property.setNeighborhood(neighborhood);
        property.setLatitude(latitude);
        property.setLongitude(longitude);
        property.setArea(area);
        property.setPrice(price);
        property.setStreetWidth(streetWidth);
        property.setFacade(facade);
        property.setPurpose(purpose);
        property.setDescription(description);
        property.setStatus(status);
    }
}
