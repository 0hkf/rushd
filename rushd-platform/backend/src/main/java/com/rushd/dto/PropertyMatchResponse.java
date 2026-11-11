package com.rushd.dto;

import com.rushd.entity.ListingType;
import com.rushd.entity.PropertyType;
import com.rushd.entity.RentalPeriod;

import java.time.LocalDateTime;
import java.util.List;

/** Public listing facts and criterion evidence only, never a valuation or publisher record. */
public record PropertyMatchResponse(
        Long id,
        String title,
        PropertyType type,
        ListingType listingType,
        RentalPeriod rentalPeriod,
        String city,
        String district,
        String price,
        String area,
        LocalDateTime createdAt,
        int preferredMatchedCount,
        int preferredCount,
        PropertyCriterionMatch districtMatch,
        PropertyCriterionMatch areaMatch,
        List<String> reasons,
        PropertyNeedsAreaBasis areaBasis,
        String matchedArea,
        List<PropertySpecificationMatch> specificationMatches,
        com.rushd.images.ImageDescriptor primaryImage) {
    public PropertyMatchResponse withPrimaryImage(com.rushd.images.ImageDescriptor image) {
        return new PropertyMatchResponse(id, title, type, listingType, rentalPeriod, city, district, price, area,
                createdAt, preferredMatchedCount, preferredCount, districtMatch, areaMatch, reasons, areaBasis,
                matchedArea, specificationMatches, image);
    }
}
