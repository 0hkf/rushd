package com.rushd.validation;

import com.rushd.dto.PropertySpecificationsRequest;
import com.rushd.entity.*;
import com.rushd.exception.InvalidPropertySpecificationsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class PropertySpecificationsRulesTest {
    @Test void patchesAndMergedCandidatesCannotMutateTheirInputs() {
        PropertySpecifications values=new PropertySpecifications(); values.setHasPool(true);
        PropertySpecificationsRequest patch=new PropertySpecificationsRequest(values,Set.of("hasPool"));
        values.setHasPool(false); patch.values().setHasPool(false);
        assertTrue(patch.values().getHasPool());
        assertThrows(UnsupportedOperationException.class,()->patch.presentFields().add("hasYard"));
        PropertySpecifications stored=new PropertySpecifications(); stored.setHasYard(false); stored.setParkingSpaces(0);
        PropertySpecifications candidate=PropertySpecificationsRules.mergeAndValidate(PropertyType.VILLA,new BigDecimal("500"),stored,patch);
        assertNull(stored.getHasPool()); assertTrue(candidate.getHasPool()); assertFalse(candidate.getHasYard());
        candidate.setParkingSpaces(5); assertEquals(0,stored.getParkingSpaces());
        assertThrows(InvalidPropertySpecificationsException.class,()->PropertySpecificationsRules.mergeAndValidate(
                PropertyType.LAND,new BigDecimal("500"),stored,patch));
        assertNull(stored.getHasPool()); assertEquals(0,stored.getParkingSpaces());
    }

    @Test void malformedFieldPrecisionUsesTheSameOverflowSafeRuleAsLegacyDecimals() {
        PropertySpecifications values=new PropertySpecifications(); values.setLandArea(new BigDecimal("0E+2147483647"));
        assertTrue(PropertySpecificationsRules.valueErrors(values,Set.of("landArea")).containsKey("landArea"));
        values.setLandArea(new BigDecimal("1.000"));
        assertTrue(PropertySpecificationsRules.valueErrors(values,Set.of("landArea")).containsKey("landArea"));
    }

    @ParameterizedTest
    @MethodSource("applicability")
    void approvedTypeAndCommercialScopeMatrixIsExplicit(PropertyType type,CommercialScope scope,String names) {
        Set<String> expected=new HashSet<>(Arrays.asList(names.split(" "))); expected.add("primaryAreaSource");
        assertEquals(expected,PropertySpecificationsRules.allowedFields(type,scope));
    }
    static Stream<Arguments> applicability() {
        String age="estimatedAgeYears ageAsOfYear condition finishing ";
        String equipment="parkingSpaces hasElevator hasAirConditioning hasFittedKitchen hasMaidRoom hasDriverRoom hasPool hasYard hasBalcony hasPrivateRoof ";
        String services="electricityConnected waterConnected sewerConnected ";
        String commercial=age+services+"commercialScope commercialSubtype bathrooms parkingSpaces hasElevator hasAirConditioning hasFittedKitchen hasPool hasYard hasBalcony hasPrivateRoof";
        return Stream.of(
                Arguments.of(PropertyType.LAND,null,"landArea parkingSpaces "+services.trim()),
                Arguments.of(PropertyType.APARTMENT,null,age+equipment+"furnishing bedrooms bathrooms livingRooms floorNumber unitNetArea unitGrossArea"),
                Arguments.of(PropertyType.VILLA,null,age+equipment+"furnishing bedrooms bathrooms livingRooms floorCount landArea totalBuiltArea"),
                Arguments.of(PropertyType.BUILDING,null,age+services+"landArea totalBuiltArea floorCount totalUnits residentialUnits commercialUnits parkingSpaces hasElevator hasAirConditioning hasPool hasYard hasPrivateRoof"),
                Arguments.of(PropertyType.COMMERCIAL,null,commercial),
                Arguments.of(PropertyType.COMMERCIAL,CommercialScope.UNIT,commercial+" unitNetArea unitGrossArea floorNumber"),
                Arguments.of(PropertyType.COMMERCIAL,CommercialScope.WHOLE_BUILDING,commercial+" landArea totalBuiltArea floorCount totalUnits residentialUnits commercialUnits"),
                Arguments.of(PropertyType.FARM,null,"landArea totalBuiltArea parkingSpaces hasPool hasYard electricityConnected hasIrrigation wellCount waterSource"),
                Arguments.of(PropertyType.OTHER,null,equipment+"landArea unitNetArea unitGrossArea totalBuiltArea otherTypeDescription"));
    }
}
