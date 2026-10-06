package com.rushd.mapper;

import com.rushd.dto.CreatePropertyRequest;
import com.rushd.dto.PropertyResponse;
import com.rushd.dto.PublisherSummaryResponse;
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
        property.setListingType(request.getListingType());
        property.setRentalPeriod(request.getRentalPeriod());
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
        property.setListingType(request.getListingType());
        property.setRentalPeriod(request.getRentalPeriod());
    }

    public static PropertyResponse toDetailsResponse(Property property) {
        PublisherSummaryResponse publisher = new PublisherSummaryResponse(
                property.getPublisher().getId(),
                property.getPublisher().getName(),
                property.getPublisher().getRole()
        );
        return toResponse(property, publisher);
    }

    private static PropertyResponse toResponse(
            Property property,
            PublisherSummaryResponse publisher) {
        return new PropertyResponse(
                property.getId(),
                publisher,
                property.getPublisher().getId(),
                property.getPublisher().getName(),
                property.getTitle(),
                property.getType(),
                property.getCity(),
                property.getDistrict(),
                property.getGooglePlaceId(),
                property.getFormattedAddress(),
                property.getNeighborhood(),
                property.getLatitude(),
                property.getLongitude(),
                property.getListingType(),
                property.getRentalPeriod(),
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
