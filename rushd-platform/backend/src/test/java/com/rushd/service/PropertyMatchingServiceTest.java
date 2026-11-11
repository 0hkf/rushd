package com.rushd.service;

import com.rushd.dto.PropertyCriterionMatch;
import com.rushd.dto.PropertyNeedsRequest;
import com.rushd.dto.PropertyNeedsRequirement;
import com.rushd.entity.ListingType;
import com.rushd.entity.Property;
import com.rushd.entity.PropertyType;
import com.rushd.exception.PropertyMatchingTooBroadException;
import com.rushd.repository.PropertyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PropertyMatchingServiceTest {
    @Mock private PropertyRepository properties;
    @Mock private com.rushd.images.PrimaryImageLookup propertyImages;
    @InjectMocks private PropertyMatchingService matching;

    @Test
    void candidateRetrievalIsBoundedAt1001AndNeverCallsUnboundedRepositoryMethods() {
        when(properties.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<Property>(List.of()));
        matching.matches(needs(), 0, 10);
        ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
        verify(properties).findAll(any(Specification.class), page.capture());
        assertEquals(0, page.getValue().getPageNumber());
        assertEquals(1001, page.getValue().getPageSize());
        verify(properties, never()).findAll();
        verify(properties, never()).findAll(any(Specification.class));
        verify(properties, never()).save(any(Property.class));
    }

    @Test
    void broadCoreSetRejectsBeforeInspectingAnyLocationOrReturningPartialResults() {
        Page<Property> page = new PageImpl<>(List.of(property()), org.springframework.data.domain.PageRequest.of(0, 1001), 1002);
        when(properties.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        assertThrows(PropertyMatchingTooBroadException.class, () -> matching.matches(needs(), 0, 10));
    }

    @Test
    void nullAreaIsUnknownForPreferredAndExcludedForRequiredWithoutMutatingProperty() {
        Property property = property();
        when(properties.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(property)));
        PropertyNeedsRequest needs = needs();
        needs.setMinArea(new BigDecimal("100"));
        needs.setAreaRequirement(PropertyNeedsRequirement.PREFERRED);
        var preferred = matching.matches(needs, 0, 10);
        assertEquals(1, preferred.totalElements());
        assertEquals(PropertyCriterionMatch.UNKNOWN, preferred.content().get(0).areaMatch());
        assertEquals(1, preferred.content().get(0).preferredCount());
        assertEquals(0, preferred.content().get(0).preferredMatchedCount());
        assertNull(preferred.content().get(0).area());
        assertNull(property.getArea());
        needs.setAreaRequirement(PropertyNeedsRequirement.REQUIRED);
        assertEquals(0, matching.matches(needs, 0, 10).totalElements());
    }

    private PropertyNeedsRequest needs() {
        PropertyNeedsRequest needs = new PropertyNeedsRequest();
        needs.setType(PropertyType.LAND);
        needs.setListingType(ListingType.SALE);
        needs.setMaxBudget(new BigDecimal("500000"));
        return needs;
    }
    @Test void primaryLookupUsesOnlyFinalPageAndDoesNotChangeRanking() {
        var first=property();first.setId(1L);var second=property();second.setId(2L);var third=property();third.setId(3L);
        when(properties.findAll(any(Specification.class),any(Pageable.class))).thenReturn(new PageImpl<>(List.of(first,second,third)));
        var result=matching.matches(needs(),1,1);
        assertEquals(2L,result.content().get(0).id());
        verify(propertyImages).primaries(List.of(2L));
    }

    private Property property() {
        Property property = new Property();
        property.setId(1L);
        property.setType(PropertyType.LAND);
        property.setListingType(ListingType.SALE);
        property.setCity("مكة");
        property.setDistrict("النوارية");
        property.setPrice(new BigDecimal("100"));
        return property;
    }
}
