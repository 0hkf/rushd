package com.rushd.repository.specification;

import com.rushd.entity.Property;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.PropertyType;
import com.rushd.entity.ListingType;
import com.rushd.entity.RentalPeriod;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PropertySpecification {

    private PropertySpecification() {
    }

    public static Specification<Property> activeProperties(
            String city,
            String district,
            PropertyType type,
            ListingType listingType,
            RentalPeriod rentalPeriod,
            BigDecimal minPrice,
            BigDecimal maxPrice) {
        return (root, query, criteriaBuilder) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(criteriaBuilder.equal(root.get("status"), PropertyStatus.ACTIVE));

            if (city != null && !city.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("city")),
                        city.trim().toLowerCase(Locale.ROOT)));
            }
            if (district != null && !district.isBlank()) {
                predicates.add(criteriaBuilder.equal(
                        criteriaBuilder.lower(root.get("district")),
                        district.trim().toLowerCase(Locale.ROOT)));
            }
            if (type != null) {
                predicates.add(criteriaBuilder.equal(root.get("type"), type));
            }
            if (listingType != null) {
                predicates.add(criteriaBuilder.equal(root.get("listingType"), listingType));
            }
            if (rentalPeriod != null) {
                predicates.add(criteriaBuilder.equal(root.get("rentalPeriod"), rentalPeriod));
            }
            if (minPrice != null) {
                predicates.add(criteriaBuilder.greaterThanOrEqualTo(
                        root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(criteriaBuilder.lessThanOrEqualTo(
                        root.get("price"), maxPrice));
            }

            return criteriaBuilder.and(predicates.toArray(new Predicate[0]));
        };
    }
}
