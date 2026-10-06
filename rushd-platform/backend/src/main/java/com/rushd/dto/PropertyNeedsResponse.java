package com.rushd.dto;

import com.rushd.entity.ListingType;
import com.rushd.entity.PropertyType;
import com.rushd.entity.RentalPeriod;

import java.math.BigDecimal;

/** Validated input only: it contains no matching results, market rating, or persistence identity. */
public record PropertyNeedsResponse(
        ListingType listingType,
        RentalPeriod rentalPeriod,
        PropertyType type,
        String maxBudget,
        String district,
        PropertyNeedsRequirement districtRequirement,
        String minArea,
        String maxArea,
        PropertyNeedsRequirement areaRequirement,
        PropertySearchGoal searchGoal,
        String city,
        String currency,
        String areaUnit) {

    public static PropertyNeedsResponse from(PropertyNeedsRequest request) {
        boolean hasArea = request.getMinArea() != null || request.getMaxArea() != null;
        return new PropertyNeedsResponse(
                request.getListingType(), request.getRentalPeriod(), request.getType(),
                decimal(request.getMaxBudget()), request.getDistrict(),
                request.getDistrict() == null ? null : defaultRequirement(
                        request.getDistrictRequirement(), PropertyNeedsRequirement.PREFERRED),
                decimal(request.getMinArea()), decimal(request.getMaxArea()),
                hasArea ? defaultRequirement(request.getAreaRequirement(), PropertyNeedsRequirement.REQUIRED) : null,
                request.getSearchGoal(), "مكة المكرمة", "SAR", "SQUARE_METRE");
    }

    private static PropertyNeedsRequirement defaultRequirement(
            PropertyNeedsRequirement supplied, PropertyNeedsRequirement fallback) {
        return supplied == null ? fallback : supplied;
    }

    private static String decimal(BigDecimal value) {
        return value == null ? null : value.toPlainString();
    }
}
