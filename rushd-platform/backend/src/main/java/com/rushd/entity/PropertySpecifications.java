package com.rushd.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.math.BigDecimal;

/** Optional facts about the listed property, never inferred from its type or description. */
@Embeddable
public class PropertySpecifications {
    @Enumerated(EnumType.STRING)
    @Column(name = "spec_primary_area_source", length = 32)
    private PropertyAreaSource primaryAreaSource;

    @Column(name = "spec_land_area", precision = 12, scale = 2)
    private BigDecimal landArea;

    @Column(name = "spec_unit_net_area", precision = 12, scale = 2)
    private BigDecimal unitNetArea;

    @Column(name = "spec_unit_gross_area", precision = 12, scale = 2)
    private BigDecimal unitGrossArea;

    @Column(name = "spec_total_built_area", precision = 12, scale = 2)
    private BigDecimal totalBuiltArea;

    @Column(name = "spec_bedrooms")
    private Integer bedrooms;

    @Column(name = "spec_bathrooms")
    private Integer bathrooms;

    @Column(name = "spec_living_rooms")
    private Integer livingRooms;

    @Column(name = "spec_floor_number")
    private Integer floorNumber;

    @Column(name = "spec_floor_count")
    private Integer floorCount;

    @Column(name = "spec_total_units")
    private Integer totalUnits;

    @Column(name = "spec_residential_units")
    private Integer residentialUnits;

    @Column(name = "spec_commercial_units")
    private Integer commercialUnits;

    @Column(name = "spec_parking_spaces")
    private Integer parkingSpaces;

    @Column(name = "spec_estimated_age_years")
    private Integer estimatedAgeYears;

    @Column(name = "spec_age_as_of_year")
    private Integer ageAsOfYear;

    @Enumerated(EnumType.STRING)
    @Column(name = "spec_condition", length = 32)
    private PropertyCondition condition;

    @Enumerated(EnumType.STRING)
    @Column(name = "spec_finishing", length = 32)
    private PropertyFinishing finishing;

    @Enumerated(EnumType.STRING)
    @Column(name = "spec_furnishing", length = 32)
    private PropertyFurnishing furnishing;

    @Column(name = "spec_has_elevator")
    private Boolean hasElevator;

    @Column(name = "spec_has_air_conditioning")
    private Boolean hasAirConditioning;

    @Column(name = "spec_has_fitted_kitchen")
    private Boolean hasFittedKitchen;

    @Column(name = "spec_has_maid_room")
    private Boolean hasMaidRoom;

    @Column(name = "spec_has_driver_room")
    private Boolean hasDriverRoom;

    @Column(name = "spec_has_pool")
    private Boolean hasPool;

    @Column(name = "spec_has_yard")
    private Boolean hasYard;

    @Column(name = "spec_has_balcony")
    private Boolean hasBalcony;

    @Column(name = "spec_has_private_roof")
    private Boolean hasPrivateRoof;

    @Column(name = "spec_electricity_connected")
    private Boolean electricityConnected;

    @Column(name = "spec_water_connected")
    private Boolean waterConnected;

    @Column(name = "spec_sewer_connected")
    private Boolean sewerConnected;

    @Column(name = "spec_has_irrigation")
    private Boolean hasIrrigation;

    @Column(name = "spec_well_count")
    private Integer wellCount;

    @Enumerated(EnumType.STRING)
    @Column(name = "spec_water_source", length = 32)
    private PropertyWaterSource waterSource;

    @Enumerated(EnumType.STRING)
    @Column(name = "spec_commercial_scope", length = 32)
    private CommercialScope commercialScope;

    @Enumerated(EnumType.STRING)
    @Column(name = "spec_commercial_subtype", length = 32)
    private CommercialSubtype commercialSubtype;

    @Column(name = "spec_other_type_description", length = 100)
    private String otherTypeDescription;

    public PropertySpecifications copy() {
        PropertySpecifications copy = new PropertySpecifications();
        copy.primaryAreaSource = primaryAreaSource;
        copy.landArea = landArea;
        copy.unitNetArea = unitNetArea;
        copy.unitGrossArea = unitGrossArea;
        copy.totalBuiltArea = totalBuiltArea;
        copy.bedrooms = bedrooms;
        copy.bathrooms = bathrooms;
        copy.livingRooms = livingRooms;
        copy.floorNumber = floorNumber;
        copy.floorCount = floorCount;
        copy.totalUnits = totalUnits;
        copy.residentialUnits = residentialUnits;
        copy.commercialUnits = commercialUnits;
        copy.parkingSpaces = parkingSpaces;
        copy.estimatedAgeYears = estimatedAgeYears;
        copy.ageAsOfYear = ageAsOfYear;
        copy.condition = condition;
        copy.finishing = finishing;
        copy.furnishing = furnishing;
        copy.hasElevator = hasElevator;
        copy.hasAirConditioning = hasAirConditioning;
        copy.hasFittedKitchen = hasFittedKitchen;
        copy.hasMaidRoom = hasMaidRoom;
        copy.hasDriverRoom = hasDriverRoom;
        copy.hasPool = hasPool;
        copy.hasYard = hasYard;
        copy.hasBalcony = hasBalcony;
        copy.hasPrivateRoof = hasPrivateRoof;
        copy.electricityConnected = electricityConnected;
        copy.waterConnected = waterConnected;
        copy.sewerConnected = sewerConnected;
        copy.hasIrrigation = hasIrrigation;
        copy.wellCount = wellCount;
        copy.waterSource = waterSource;
        copy.commercialScope = commercialScope;
        copy.commercialSubtype = commercialSubtype;
        copy.otherTypeDescription = otherTypeDescription;
        return copy;
    }

    public PropertyAreaSource getPrimaryAreaSource() { return primaryAreaSource; }
    public void setPrimaryAreaSource(PropertyAreaSource value) { primaryAreaSource = value; }

    public BigDecimal getLandArea() { return landArea; }
    public void setLandArea(BigDecimal value) { landArea = value; }

    public BigDecimal getUnitNetArea() { return unitNetArea; }
    public void setUnitNetArea(BigDecimal value) { unitNetArea = value; }

    public BigDecimal getUnitGrossArea() { return unitGrossArea; }
    public void setUnitGrossArea(BigDecimal value) { unitGrossArea = value; }

    public BigDecimal getTotalBuiltArea() { return totalBuiltArea; }
    public void setTotalBuiltArea(BigDecimal value) { totalBuiltArea = value; }

    public Integer getBedrooms() { return bedrooms; }
    public void setBedrooms(Integer value) { bedrooms = value; }

    public Integer getBathrooms() { return bathrooms; }
    public void setBathrooms(Integer value) { bathrooms = value; }

    public Integer getLivingRooms() { return livingRooms; }
    public void setLivingRooms(Integer value) { livingRooms = value; }

    public Integer getFloorNumber() { return floorNumber; }
    public void setFloorNumber(Integer value) { floorNumber = value; }

    public Integer getFloorCount() { return floorCount; }
    public void setFloorCount(Integer value) { floorCount = value; }

    public Integer getTotalUnits() { return totalUnits; }
    public void setTotalUnits(Integer value) { totalUnits = value; }

    public Integer getResidentialUnits() { return residentialUnits; }
    public void setResidentialUnits(Integer value) { residentialUnits = value; }

    public Integer getCommercialUnits() { return commercialUnits; }
    public void setCommercialUnits(Integer value) { commercialUnits = value; }

    public Integer getParkingSpaces() { return parkingSpaces; }
    public void setParkingSpaces(Integer value) { parkingSpaces = value; }

    public Integer getEstimatedAgeYears() { return estimatedAgeYears; }
    public void setEstimatedAgeYears(Integer value) { estimatedAgeYears = value; }

    public Integer getAgeAsOfYear() { return ageAsOfYear; }
    public void setAgeAsOfYear(Integer value) { ageAsOfYear = value; }

    public PropertyCondition getCondition() { return condition; }
    public void setCondition(PropertyCondition value) { condition = value; }

    public PropertyFinishing getFinishing() { return finishing; }
    public void setFinishing(PropertyFinishing value) { finishing = value; }

    public PropertyFurnishing getFurnishing() { return furnishing; }
    public void setFurnishing(PropertyFurnishing value) { furnishing = value; }

    public Boolean getHasElevator() { return hasElevator; }
    public void setHasElevator(Boolean value) { hasElevator = value; }

    public Boolean getHasAirConditioning() { return hasAirConditioning; }
    public void setHasAirConditioning(Boolean value) { hasAirConditioning = value; }

    public Boolean getHasFittedKitchen() { return hasFittedKitchen; }
    public void setHasFittedKitchen(Boolean value) { hasFittedKitchen = value; }

    public Boolean getHasMaidRoom() { return hasMaidRoom; }
    public void setHasMaidRoom(Boolean value) { hasMaidRoom = value; }

    public Boolean getHasDriverRoom() { return hasDriverRoom; }
    public void setHasDriverRoom(Boolean value) { hasDriverRoom = value; }

    public Boolean getHasPool() { return hasPool; }
    public void setHasPool(Boolean value) { hasPool = value; }

    public Boolean getHasYard() { return hasYard; }
    public void setHasYard(Boolean value) { hasYard = value; }

    public Boolean getHasBalcony() { return hasBalcony; }
    public void setHasBalcony(Boolean value) { hasBalcony = value; }

    public Boolean getHasPrivateRoof() { return hasPrivateRoof; }
    public void setHasPrivateRoof(Boolean value) { hasPrivateRoof = value; }

    public Boolean getElectricityConnected() { return electricityConnected; }
    public void setElectricityConnected(Boolean value) { electricityConnected = value; }

    public Boolean getWaterConnected() { return waterConnected; }
    public void setWaterConnected(Boolean value) { waterConnected = value; }

    public Boolean getSewerConnected() { return sewerConnected; }
    public void setSewerConnected(Boolean value) { sewerConnected = value; }

    public Boolean getHasIrrigation() { return hasIrrigation; }
    public void setHasIrrigation(Boolean value) { hasIrrigation = value; }

    public Integer getWellCount() { return wellCount; }
    public void setWellCount(Integer value) { wellCount = value; }

    public PropertyWaterSource getWaterSource() { return waterSource; }
    public void setWaterSource(PropertyWaterSource value) { waterSource = value; }

    public CommercialScope getCommercialScope() { return commercialScope; }
    public void setCommercialScope(CommercialScope value) { commercialScope = value; }

    public CommercialSubtype getCommercialSubtype() { return commercialSubtype; }
    public void setCommercialSubtype(CommercialSubtype value) { commercialSubtype = value; }

    public String getOtherTypeDescription() { return otherTypeDescription; }
    public void setOtherTypeDescription(String value) { otherTypeDescription = value; }

}
