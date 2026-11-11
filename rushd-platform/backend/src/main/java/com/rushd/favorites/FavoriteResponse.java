package com.rushd.favorites;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.rushd.dto.serialization.PlainDecimalStringSerializer;
import com.rushd.entity.*;
import com.rushd.images.ImageDescriptor;
import java.math.BigDecimal;
import java.time.Instant;

public record FavoriteResponse(String propertyId, Instant createdAt, boolean available, Summary property) {
    public record Summary(String id, String title, PropertyType type, ListingType listingType,
                          RentalPeriod rentalPeriod, String city, String district,
                          @JsonSerialize(using = PlainDecimalStringSerializer.class) BigDecimal price,
                          @JsonSerialize(using = PlainDecimalStringSerializer.class) BigDecimal area,
                          PropertyStatus status, ImageDescriptor primaryImage) {
        static Summary from(Property property, ImageDescriptor primary) {
            return new Summary(property.getId().toString(), property.getTitle(), property.getType(), property.getListingType(),
                    property.getRentalPeriod(), property.getCity(), property.getDistrict(), property.getPrice(), property.getArea(), property.getStatus(), primary);
        }
    }
}
