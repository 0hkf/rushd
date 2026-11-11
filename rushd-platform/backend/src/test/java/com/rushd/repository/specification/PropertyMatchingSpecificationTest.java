package com.rushd.repository.specification;

import com.rushd.dto.PropertyNeedsRequest;
import com.rushd.entity.ListingType;
import com.rushd.entity.Property;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.PropertyType;
import com.rushd.entity.RentalPeriod;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Tests the defensive criteria guard without inserting rows forbidden by the PostgreSQL baseline. */
@ExtendWith(MockitoExtension.class)
class PropertyMatchingSpecificationTest {
    @Mock private Root<Property> root;
    @Mock private CriteriaQuery<?> query;
    @Mock private CriteriaBuilder builder;
    @Mock private Path<Object> status;
    @Mock private Path<Object> type;
    @Mock private Path<Object> listingType;
    @Mock private Path<Object> rentalPeriod;
    @Mock private Path<BigDecimal> price;
    @Mock private Predicate active;
    @Mock private Predicate correctType;
    @Mock private Predicate correctListing;
    @Mock private Predicate correctPeriod;
    @Mock private Predicate positivePrice;
    @Mock private Predicate withinBudget;
    @Mock private Predicate conjunction;
    private PropertyNeedsRequest needs;

    @BeforeEach
    void setup() {
        needs = new PropertyNeedsRequest();
        needs.setType(PropertyType.LAND);
        needs.setMaxBudget(new BigDecimal("500000.00"));
        when(root.get("status")).thenReturn(status);
        when(root.get("type")).thenReturn(type);
        when(root.get("listingType")).thenReturn(listingType);
        when(root.get("rentalPeriod")).thenReturn(rentalPeriod);
        when(root.<BigDecimal>get("price")).thenReturn(price);
        when(builder.equal(status, PropertyStatus.ACTIVE)).thenReturn(active);
        when(builder.equal(type, PropertyType.LAND)).thenReturn(correctType);
        when(builder.greaterThan(price, BigDecimal.ZERO)).thenReturn(positivePrice);
        when(builder.lessThanOrEqualTo(price, needs.getMaxBudget())).thenReturn(withinBudget);
        when(builder.and(any(Predicate[].class))).thenReturn(conjunction);
    }

    @Test
    void saleAlwaysRequiresNullRentalPeriodInTheFinalConjunction() {
        needs.setListingType(ListingType.SALE);
        when(builder.equal(listingType, ListingType.SALE)).thenReturn(correctListing);
        when(builder.isNull(rentalPeriod)).thenReturn(correctPeriod);

        assertSame(conjunction, PropertyMatchingSpecification.coreCandidates(needs).toPredicate(root, query, builder));

        verify(builder).isNull(rentalPeriod);
        assertAllCorePredicatesAreRequired();
    }

    @ParameterizedTest
    @EnumSource(RentalPeriod.class)
    void rentalAlwaysRequiresItsExactRequestedPeriodInTheFinalConjunction(RentalPeriod period) {
        needs.setListingType(ListingType.RENT);
        needs.setRentalPeriod(period);
        when(builder.equal(listingType, ListingType.RENT)).thenReturn(correctListing);
        when(builder.equal(rentalPeriod, period)).thenReturn(correctPeriod);

        assertSame(conjunction, PropertyMatchingSpecification.coreCandidates(needs).toPredicate(root, query, builder));

        verify(builder).equal(rentalPeriod, period);
        verify(builder, never()).isNull(rentalPeriod);
        assertAllCorePredicatesAreRequired();
    }

    private void assertAllCorePredicatesAreRequired() {
        ArgumentCaptor<Predicate[]> predicates = ArgumentCaptor.forClass(Predicate[].class);
        verify(builder).and(predicates.capture());
        assertTrue(Arrays.asList(predicates.getValue()).containsAll(
                Arrays.asList(active, correctType, correctListing, correctPeriod, positivePrice, withinBudget)));
    }
}
