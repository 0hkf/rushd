package com.rushd.validation;

import com.rushd.entity.*;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/** Explicit allowlist shared by patch binding, merging and value validation. No reflection binding. */
public final class PropertySpecificationFields {
    public record Field<T>(String name, Class<T> type, Function<PropertySpecifications, T> read,
                           BiConsumer<PropertySpecifications, T> write, Integer min, Integer max) {
        public void apply(PropertySpecifications target, PropertySpecifications source) {
            write.accept(target, read.apply(source));
        }
    }
    private PropertySpecificationFields() { }
    public static final List<Field<?>> ALL = List.of(
            new Field<>("primaryAreaSource", PropertyAreaSource.class, PropertySpecifications::getPrimaryAreaSource, PropertySpecifications::setPrimaryAreaSource, null, null),
            new Field<>("landArea", BigDecimal.class, PropertySpecifications::getLandArea, PropertySpecifications::setLandArea, null, null),
            new Field<>("unitNetArea", BigDecimal.class, PropertySpecifications::getUnitNetArea, PropertySpecifications::setUnitNetArea, null, null),
            new Field<>("unitGrossArea", BigDecimal.class, PropertySpecifications::getUnitGrossArea, PropertySpecifications::setUnitGrossArea, null, null),
            new Field<>("totalBuiltArea", BigDecimal.class, PropertySpecifications::getTotalBuiltArea, PropertySpecifications::setTotalBuiltArea, null, null),
            new Field<>("bedrooms", Integer.class, PropertySpecifications::getBedrooms, PropertySpecifications::setBedrooms, 0, 100),
            new Field<>("bathrooms", Integer.class, PropertySpecifications::getBathrooms, PropertySpecifications::setBathrooms, 0, 100),
            new Field<>("livingRooms", Integer.class, PropertySpecifications::getLivingRooms, PropertySpecifications::setLivingRooms, 0, 100),
            new Field<>("floorNumber", Integer.class, PropertySpecifications::getFloorNumber, PropertySpecifications::setFloorNumber, -10, 200),
            new Field<>("floorCount", Integer.class, PropertySpecifications::getFloorCount, PropertySpecifications::setFloorCount, 1, 200),
            new Field<>("totalUnits", Integer.class, PropertySpecifications::getTotalUnits, PropertySpecifications::setTotalUnits, 0, 10000),
            new Field<>("residentialUnits", Integer.class, PropertySpecifications::getResidentialUnits, PropertySpecifications::setResidentialUnits, 0, 10000),
            new Field<>("commercialUnits", Integer.class, PropertySpecifications::getCommercialUnits, PropertySpecifications::setCommercialUnits, 0, 10000),
            new Field<>("parkingSpaces", Integer.class, PropertySpecifications::getParkingSpaces, PropertySpecifications::setParkingSpaces, 0, 1000),
            new Field<>("estimatedAgeYears", Integer.class, PropertySpecifications::getEstimatedAgeYears, PropertySpecifications::setEstimatedAgeYears, 0, 1000),
            new Field<>("ageAsOfYear", Integer.class, PropertySpecifications::getAgeAsOfYear, PropertySpecifications::setAgeAsOfYear, 1900, 9999),
            new Field<>("condition", PropertyCondition.class, PropertySpecifications::getCondition, PropertySpecifications::setCondition, null, null),
            new Field<>("finishing", PropertyFinishing.class, PropertySpecifications::getFinishing, PropertySpecifications::setFinishing, null, null),
            new Field<>("furnishing", PropertyFurnishing.class, PropertySpecifications::getFurnishing, PropertySpecifications::setFurnishing, null, null),
            new Field<>("hasElevator", Boolean.class, PropertySpecifications::getHasElevator, PropertySpecifications::setHasElevator, null, null),
            new Field<>("hasAirConditioning", Boolean.class, PropertySpecifications::getHasAirConditioning, PropertySpecifications::setHasAirConditioning, null, null),
            new Field<>("hasFittedKitchen", Boolean.class, PropertySpecifications::getHasFittedKitchen, PropertySpecifications::setHasFittedKitchen, null, null),
            new Field<>("hasMaidRoom", Boolean.class, PropertySpecifications::getHasMaidRoom, PropertySpecifications::setHasMaidRoom, null, null),
            new Field<>("hasDriverRoom", Boolean.class, PropertySpecifications::getHasDriverRoom, PropertySpecifications::setHasDriverRoom, null, null),
            new Field<>("hasPool", Boolean.class, PropertySpecifications::getHasPool, PropertySpecifications::setHasPool, null, null),
            new Field<>("hasYard", Boolean.class, PropertySpecifications::getHasYard, PropertySpecifications::setHasYard, null, null),
            new Field<>("hasBalcony", Boolean.class, PropertySpecifications::getHasBalcony, PropertySpecifications::setHasBalcony, null, null),
            new Field<>("hasPrivateRoof", Boolean.class, PropertySpecifications::getHasPrivateRoof, PropertySpecifications::setHasPrivateRoof, null, null),
            new Field<>("electricityConnected", Boolean.class, PropertySpecifications::getElectricityConnected, PropertySpecifications::setElectricityConnected, null, null),
            new Field<>("waterConnected", Boolean.class, PropertySpecifications::getWaterConnected, PropertySpecifications::setWaterConnected, null, null),
            new Field<>("sewerConnected", Boolean.class, PropertySpecifications::getSewerConnected, PropertySpecifications::setSewerConnected, null, null),
            new Field<>("hasIrrigation", Boolean.class, PropertySpecifications::getHasIrrigation, PropertySpecifications::setHasIrrigation, null, null),
            new Field<>("wellCount", Integer.class, PropertySpecifications::getWellCount, PropertySpecifications::setWellCount, 0, 100),
            new Field<>("waterSource", PropertyWaterSource.class, PropertySpecifications::getWaterSource, PropertySpecifications::setWaterSource, null, null),
            new Field<>("commercialScope", CommercialScope.class, PropertySpecifications::getCommercialScope, PropertySpecifications::setCommercialScope, null, null),
            new Field<>("commercialSubtype", CommercialSubtype.class, PropertySpecifications::getCommercialSubtype, PropertySpecifications::setCommercialSubtype, null, null),
            new Field<>("otherTypeDescription", String.class, PropertySpecifications::getOtherTypeDescription, PropertySpecifications::setOtherTypeDescription, null, null));
    public static final Map<String, Field<?>> BY_NAME;
    static {
        Map<String, Field<?>> fields = new LinkedHashMap<>();
        ALL.forEach(field -> fields.put(field.name(), field));
        BY_NAME = Collections.unmodifiableMap(fields);
    }
}
