package com.rushd.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rushd.dto.*;
import com.rushd.dto.SelectedPropertyEvaluation.*;
import com.rushd.entity.*;
import com.rushd.exception.PropertyNotFoundException;
import com.rushd.repository.PropertyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;

class SelectedPropertyEvaluationServiceTest {
    private PropertyRepository repository;
    private SelectedPropertyEvaluationService service;
    private Property property;
    private PropertyNeedsRequest needs;

    @BeforeEach void setup() {
        repository = (PropertyRepository) java.lang.reflect.Proxy.newProxyInstance(
                PropertyRepository.class.getClassLoader(), new Class<?>[]{PropertyRepository.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("findById")) return Long.valueOf(1).equals(args[0]) ? Optional.of(property) : Optional.empty();
                    throw new UnsupportedOperationException(method.getName());
                });
        service = new SelectedPropertyEvaluationService(repository, new ObjectMapper());
        property = new Property(); property.setId(1L); property.setStatus(PropertyStatus.ACTIVE);
        property.setType(PropertyType.APARTMENT); property.setListingType(ListingType.SALE);
        property.setCity("مكة"); property.setDistrict("حي الياسمين");
        property.setPrice(new BigDecimal("9007199254740993.01")); property.setArea(new BigDecimal("120.01"));
        needs = new PropertyNeedsRequest(); needs.setType(PropertyType.APARTMENT); needs.setListingType(ListingType.SALE);
        needs.setMaxBudget(new BigDecimal("9007199254740993.01"));
    }
    @ParameterizedTest @EnumSource(PropertyType.class)
    void eachTypeWithLegacyRequestProducesBasicMatch(PropertyType type) {
        property.setType(type); needs.setType(type);
        var result = service.evaluate(1L, needs);
        assertEquals(Classification.BASIC_MATCH, result.classification());
        assertEquals("selected-property-evaluation-v1", result.rulesVersion());
        assertEquals("9007199254740993.01", evidence(result, "maxBudget").actual());
        assertEquals(64, result.factsFingerprint().length());
    }
    @Test void exactBudgetAndPriorityOfKnownViolationOverUnknown() {
        property.setPrice(new BigDecimal("9007199254740993.02"));
        needs.setDistrict("الياسمين"); property.setDistrict(null);
        var result = service.evaluate(1L, needs);
        assertEquals(Classification.NOT_SUITABLE, result.classification());
        assertEquals(Outcome.MISMATCHED, evidence(result, "maxBudget").outcome());
        assertEquals(Outcome.UNKNOWN, evidence(result, "district").outcome());
    }
    @Test void unicodeWhitespaceOnlyCityIsUnknown() {
        property.setCity(" \u00a0\t");
        var result = service.evaluate(1L, needs);
        assertEquals(Outcome.UNKNOWN, evidence(result, "city").outcome());
        assertEquals(Classification.INSUFFICIENT_DATA, result.classification());
    }
    @Test void preferenceThresholdsAndUnknownAreConservative() {
        needs.setDistrict("الياسمين"); needs.setMinArea(new BigDecimal("100"));
        needs.setAreaRequirement(PropertyNeedsRequirement.PREFERRED);
        assertEquals(Classification.VERY_SUITABLE, service.evaluate(1L, needs).classification());
        property.setDistrict("العزيزية");
        assertEquals(Classification.SUITABLE, service.evaluate(1L, needs).classification());
        property.setArea(new BigDecimal("90"));
        assertEquals(Classification.PARTIALLY_SUITABLE, service.evaluate(1L, needs).classification());
        property.setDistrict(null);
        assertEquals(Classification.INSUFFICIENT_DATA, service.evaluate(1L, needs).classification());
    }
    @Test void fourPreferenceBoundaryCasesRequireNoRounding() {
        needs.setDistrict("الياسمين");
        Map<String, PropertySpecificationCriterion> criteria = new LinkedHashMap<>();
        criteria.put("bedrooms", new PropertySpecificationCriterion(1, null, null, PropertyNeedsRequirement.PREFERRED));
        criteria.put("parkingSpaces", new PropertySpecificationCriterion(1, null, null, PropertyNeedsRequirement.PREFERRED));
        criteria.put("hasElevator", new PropertySpecificationCriterion(null, null, true, PropertyNeedsRequirement.PREFERRED));
        needs.setSpecificationCriteria(new PropertySpecificationCriteria(criteria));
        PropertySpecifications specs = new PropertySpecifications();
        specs.setBedrooms(1); specs.setParkingSpaces(1); specs.setHasElevator(true); property.setSpecifications(specs);
        assertEquals(Classification.VERY_SUITABLE, service.evaluate(1L, needs).classification());
        specs.setHasElevator(false);
        assertEquals(Classification.SUITABLE, service.evaluate(1L, needs).classification());
        specs.setParkingSpaces(0);
        assertEquals(Classification.SUITABLE, service.evaluate(1L, needs).classification());
        specs.setBedrooms(0);
        assertEquals(Classification.PARTIALLY_SUITABLE, service.evaluate(1L, needs).classification());
        property.setDistrict("العزيزية");
        assertEquals(Classification.PARTIALLY_SUITABLE, service.evaluate(1L, needs).classification());
    }
    @Test void differentRentalPeriodDoesNotComparePrices() {
        needs.setListingType(ListingType.RENT); needs.setRentalPeriod(RentalPeriod.YEARLY);
        property.setListingType(ListingType.RENT); property.setRentalPeriod(RentalPeriod.MONTHLY);
        var result = service.evaluate(1L, needs);
        assertEquals(Classification.NOT_SUITABLE, result.classification());
        assertEquals(Outcome.MISMATCHED, evidence(result, "rentalPeriod").outcome());
        assertEquals(Outcome.NOT_COMPARABLE, evidence(result, "maxBudget").outcome());
        property.setRentalPeriod(RentalPeriod.YEARLY);
        assertEquals(Outcome.MATCHED, evidence(service.evaluate(1L, needs), "maxBudget").outcome());
        property.setPrice(new BigDecimal("9007199254740993.02"));
        assertEquals(Outcome.MISMATCHED, evidence(service.evaluate(1L, needs), "maxBudget").outcome());
        property.setListingType(ListingType.SALE);
        assertEquals(Outcome.NOT_COMPARABLE, evidence(service.evaluate(1L, needs), "maxBudget").outcome());
    }
    @Test void incompatibleTypesNeverCompareAreaOrSpecifications() {
        property.setType(PropertyType.LAND); needs.setMinArea(BigDecimal.ONE);
        needs.setSpecificationCriteria(new PropertySpecificationCriteria(Map.of("bedrooms", new PropertySpecificationCriterion(3, null, null, PropertyNeedsRequirement.PREFERRED))));
        var result = service.evaluate(1L, needs);
        assertEquals(Classification.NOT_SUITABLE, result.classification());
        assertEquals(Outcome.NOT_COMPARABLE, evidence(result, "area").outcome());
        assertNull(evidence(result, "area").actual());
        assertEquals(Outcome.NOT_COMPARABLE, evidence(result, "specification.bedrooms").outcome());
    }
    @Test void commercialScopeMustBeKnownAndNeverComparedAcrossScopes() {
        needs.setType(PropertyType.COMMERCIAL); property.setType(PropertyType.COMMERCIAL);
        needs.setCommercialScope(CommercialScope.UNIT); needs.setMinArea(new BigDecimal("100"));
        needs.setAreaBasis(PropertyNeedsAreaBasis.UNIT_NET);
        PropertySpecifications specs = new PropertySpecifications(); specs.setUnitNetArea(new BigDecimal("120"));
        property.setSpecifications(specs);
        var unknown = service.evaluate(1L, needs);
        assertEquals(Outcome.UNKNOWN, evidence(unknown, "commercialScope").outcome());
        assertEquals(Outcome.NOT_COMPARABLE, evidence(unknown, "area").outcome());
        assertEquals(Classification.INSUFFICIENT_DATA, unknown.classification());
        specs.setCommercialScope(CommercialScope.WHOLE_BUILDING);
        var wrong = service.evaluate(1L, needs);
        assertEquals(Classification.NOT_SUITABLE, wrong.classification());
        assertEquals(Outcome.NOT_COMPARABLE, evidence(wrong, "area").outcome());
        specs.setCommercialScope(CommercialScope.UNIT);
        assertEquals(Classification.BASIC_MATCH, service.evaluate(1L, needs).classification());
    }
    @Test void zeroFalseAndUnknownRemainDistinct() {
        PropertySpecifications specs = new PropertySpecifications(); specs.setParkingSpaces(0); specs.setHasElevator(false);
        property.setSpecifications(specs);
        Map<String, PropertySpecificationCriterion> criteria = new LinkedHashMap<>();
        criteria.put("parkingSpaces", new PropertySpecificationCriterion(0, null, null, PropertyNeedsRequirement.PREFERRED));
        criteria.put("hasElevator", new PropertySpecificationCriterion(null, null, false, PropertyNeedsRequirement.PREFERRED));
        criteria.put("bedrooms", new PropertySpecificationCriterion(1, null, null, PropertyNeedsRequirement.PREFERRED));
        needs.setSpecificationCriteria(new PropertySpecificationCriteria(criteria));
        var result = service.evaluate(1L, needs);
        assertEquals(Outcome.MATCHED, evidence(result, "specification.parkingSpaces").outcome());
        assertEquals(Outcome.MATCHED, evidence(result, "specification.hasElevator").outcome());
        assertEquals(Outcome.UNKNOWN, evidence(result, "specification.bedrooms").outcome());
        assertEquals(Classification.INSUFFICIENT_DATA, result.classification());
    }
    @Test void reEvaluationReadsFreshFactsAndRejectsAllUnavailableStatuses() {
        var first = service.evaluate(1L, needs); property.setPrice(new BigDecimal("100"));
        var second = service.evaluate(1L, needs);
        assertNotEquals(first.factsFingerprint(), second.factsFingerprint());
        for (PropertyStatus status : PropertyStatus.values()) {
            if (status == PropertyStatus.ACTIVE) continue;
            property.setStatus(status); assertThrows(PropertyNotFoundException.class, () -> service.evaluate(1L, needs));
        }
        assertThrows(PropertyNotFoundException.class, () -> service.evaluate(2L, needs));
    }
    private Evidence evidence(SelectedPropertyEvaluation result, String id) {
        return result.evidence().stream().filter(item -> item.id().equals(id)).findFirst().orElseThrow();
    }
}
