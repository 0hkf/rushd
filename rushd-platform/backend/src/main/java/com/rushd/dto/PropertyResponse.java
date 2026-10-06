package com.rushd.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.rushd.entity.ListingType;
import com.rushd.entity.RentalPeriod;
import com.rushd.entity.PropertyFacade;
import com.rushd.entity.PropertyPurpose;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.PropertyType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PropertyResponse {

    private final Long id;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private final PublisherSummaryResponse publisher;
    private final Long publisherId;
    private final String publisherName;
    private final String title;
    private final PropertyType type;
    private final ListingType listingType;
    private final RentalPeriod rentalPeriod;
    private final String city;
    private final String district;
    private final String googlePlaceId;
    private final String formattedAddress;
    private final String neighborhood;
    private final BigDecimal latitude;
    private final BigDecimal longitude;
    private final BigDecimal area;
    private final BigDecimal price;
    private final BigDecimal streetWidth;
    private final PropertyFacade facade;
    private final PropertyPurpose purpose;
    private final String description;
    private final PropertyStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public PropertyResponse(Long id,
                            Long publisherId,
                            String publisherName,
                            String title,
                            PropertyType type,
                            String city,
                            String district,
                            BigDecimal area,
                            BigDecimal price,
                            BigDecimal streetWidth,
                            PropertyFacade facade,
                            PropertyPurpose purpose,
                            String description,
                            PropertyStatus status,
                            LocalDateTime createdAt,
                            LocalDateTime updatedAt) {
        this(id, null, publisherId, publisherName, title, type, city, district, area, price,
                streetWidth, facade, purpose, description, status, createdAt, updatedAt);
    }

    public PropertyResponse(Long id,
                            PublisherSummaryResponse publisher,
                            Long publisherId,
                            String publisherName,
                            String title,
                            PropertyType type,
                            String city,
                            String district,
                            BigDecimal area,
                            BigDecimal price,
                            BigDecimal streetWidth,
                            PropertyFacade facade,
                            PropertyPurpose purpose,
                            String description,
                            PropertyStatus status,
                            LocalDateTime createdAt,
                            LocalDateTime updatedAt) {
        this(id, publisher, publisherId, publisherName, title, type, city, district,
                null, null, null, null, null, ListingType.SALE, null, area, price, streetWidth, facade,
                purpose, description, status, createdAt, updatedAt);
    }

    public PropertyResponse(Long id,
                            PublisherSummaryResponse publisher,
                            Long publisherId,
                            String publisherName,
                            String title,
                            PropertyType type,
                            String city,
                            String district,
                            String googlePlaceId,
                            String formattedAddress,
                            String neighborhood,
                            BigDecimal latitude,
                            BigDecimal longitude,
                            ListingType listingType,
                            RentalPeriod rentalPeriod,
                            BigDecimal area,
                            BigDecimal price,
                            BigDecimal streetWidth,
                            PropertyFacade facade,
                            PropertyPurpose purpose,
                            String description,
                            PropertyStatus status,
                            LocalDateTime createdAt,
                            LocalDateTime updatedAt) {
        this.id = id;
        this.publisher = publisher;
        this.publisherId = publisherId;
        this.publisherName = publisherName;
        this.title = title;
        this.type = type;
        this.city = city;
        this.district = district;
        this.googlePlaceId = googlePlaceId;
        this.formattedAddress = formattedAddress;
        this.neighborhood = neighborhood;
        this.latitude = latitude;
        this.longitude = longitude;
        this.listingType = listingType;
        this.rentalPeriod = rentalPeriod;
        this.area = area;
        this.price = price;
        this.streetWidth = streetWidth;
        this.facade = facade;
        this.purpose = purpose;
        this.description = description;
        this.status = status;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long getId() {
        return id;
    }

    public PublisherSummaryResponse getPublisher() {
        return publisher;
    }

    public Long getPublisherId() {
        return publisherId;
    }

    public String getPublisherName() {
        return publisherName;
    }

    public String getTitle() {
        return title;
    }

    public PropertyType getType() {
        return type;
    }

    public String getCity() {
        return city;
    }

    public String getDistrict() {
        return district;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public String getFormattedAddress() {
        return formattedAddress;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public ListingType getListingType() {
        return listingType;
    }

    public RentalPeriod getRentalPeriod() {
        return rentalPeriod;
    }

    public BigDecimal getArea() {
        return area;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public BigDecimal getStreetWidth() {
        return streetWidth;
    }

    public PropertyFacade getFacade() {
        return facade;
    }

    public PropertyPurpose getPurpose() {
        return purpose;
    }

    public String getDescription() {
        return description;
    }

    public PropertyStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
