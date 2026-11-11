package com.rushd.repository.specification;

import com.rushd.dto.PropertyNeedsRequest;
import com.rushd.dto.PropertyNeedsRequirement;
import com.rushd.entity.ListingType;
import com.rushd.entity.Property;
import com.rushd.entity.PropertyStatus;
import com.rushd.dto.PropertyNeedsAreaBasis;
import com.rushd.validation.PropertyMatchingFields;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/** Bounded candidate eligibility; location aliases are evaluated separately without database rewrites. */
public final class PropertyMatchingSpecification {
    private PropertyMatchingSpecification() {}

    public static Specification<Property> coreCandidates(PropertyNeedsRequest needs) {
        return (root, query, builder) -> {
            List<Predicate> conditions = new ArrayList<>();
            conditions.add(builder.equal(root.get("status"), PropertyStatus.ACTIVE));
            conditions.add(builder.equal(root.get("type"), needs.getType()));
            conditions.add(builder.equal(root.get("listingType"), needs.getListingType()));
            if (needs.getListingType() == ListingType.RENT) {
                conditions.add(builder.equal(root.get("rentalPeriod"), needs.getRentalPeriod()));
            } else {
                conditions.add(builder.isNull(root.get("rentalPeriod")));
            }
            conditions.add(builder.greaterThan(root.get("price"), BigDecimal.ZERO));
            conditions.add(builder.lessThanOrEqualTo(root.get("price"), needs.getMaxBudget()));
            if (needs.getCommercialScope() != null) conditions.add(builder.equal(root.get("specifications").get("commercialScope"), needs.getCommercialScope()));

            boolean hasArea = needs.getMinArea() != null || needs.getMaxArea() != null;
            boolean requiredArea = hasArea && needs.getAreaRequirement() != PropertyNeedsRequirement.PREFERRED;
            if (requiredArea) {
                jakarta.persistence.criteria.Path<BigDecimal> area = needs.getAreaBasis() == PropertyNeedsAreaBasis.ADVERTISED
                        ? root.get("area") : root.get("specifications").get(needs.getAreaBasis().field());
                conditions.add(builder.greaterThan(area, BigDecimal.ZERO));
                if (needs.getMinArea() != null) {
                    conditions.add(builder.greaterThanOrEqualTo(area, needs.getMinArea()));
                }
                if (needs.getMaxArea() != null) {
                    conditions.add(builder.lessThanOrEqualTo(area, needs.getMaxArea()));
                }
            }
            needs.getSpecificationCriteria().values().forEach((name, criterion) -> {
                if (criterion.requirement() != PropertyNeedsRequirement.REQUIRED) return;
                var field = PropertyMatchingFields.ALL.get(name);
                if (field.type() == Integer.class) {
                    jakarta.persistence.criteria.Path<Integer> value = root.get("specifications").get(name);
                    if (criterion.min() != null) conditions.add(builder.greaterThanOrEqualTo(value, criterion.min()));
                    if (criterion.max() != null) conditions.add(builder.lessThanOrEqualTo(value, criterion.max()));
                } else conditions.add(builder.equal(root.get("specifications").get(name), criterion.value()));
            });
            return builder.and(conditions.toArray(new Predicate[0]));
        };
    }
}
