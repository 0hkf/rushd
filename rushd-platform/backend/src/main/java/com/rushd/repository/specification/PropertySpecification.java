package com.rushd.repository.specification;

import com.rushd.entity.Property;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.PropertyType;
import com.rushd.entity.ListingType;
import com.rushd.entity.RentalPeriod;
import com.rushd.service.PropertyLocationComparison;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PropertySpecification {

    // Character.isWhitespace/isSpaceChar spaces, collapsed before comparison only.
    // regexp_replace with the global flag is supported by the verified PG/H2 runtimes.
    private static final String LOCATION_SPACES = "[\t-\r\034-\040\u00A0\u1680\u2000-\u200A\u2028\u2029\u202F\u205F\u3000]+";

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
                if (PropertyLocationComparison.isMakkah(city)) {
                    var key = criteriaBuilder.lower(criteriaBuilder.trim(criteriaBuilder.function(
                            "regexp_replace", String.class, root.get("city"),
                            criteriaBuilder.literal(LOCATION_SPACES), criteriaBuilder.literal(" "),
                            criteriaBuilder.literal("g"))));
                    predicates.add(key.in(PropertyLocationComparison.makkahNames()));
                } else predicates.add(criteriaBuilder.equal(
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
