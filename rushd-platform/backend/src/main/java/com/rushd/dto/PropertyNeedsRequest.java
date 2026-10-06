package com.rushd.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rushd.dto.deserialization.StrictStringEnumDeserializer;
import com.rushd.entity.ListingType;
import com.rushd.entity.PropertyType;
import com.rushd.entity.RentalPeriod;
import com.rushd.validation.ConsistentPropertyNeeds;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

@ConsistentPropertyNeeds
public class PropertyNeedsRequest {
    @NotNull(message = "نوع العرض مطلوب")
    @JsonDeserialize(using = StrictStringEnumDeserializer.class)
    private ListingType listingType;

    @JsonDeserialize(using = StrictStringEnumDeserializer.class)
    private RentalPeriod rentalPeriod;

    @NotNull(message = "نوع العقار مطلوب")
    @JsonDeserialize(using = StrictStringEnumDeserializer.class)
    private PropertyType type;

    @NotNull(message = "الحد الأعلى للميزانية مطلوب")
    @Positive(message = "الميزانية يجب أن تكون أكبر من صفر")
    @DecimalMin(value = "0.01", message = "الميزانية يجب ألا تقل عن 0.01 ريال")
    @DecimalMax(value = "99999999999999999.99", message = "الميزانية تتجاوز الحد المسموح")
    @Digits(integer = 17, fraction = 2, message = "الميزانية تقبل حتى 17 رقماً صحيحاً ومنزلتين عشريتين")
    private BigDecimal maxBudget;

    @Size(max = 100, message = "المدينة يجب ألا تتجاوز 100 حرف")
    @Pattern(regexp = "مكة المكرمة", message = "الترشيح في هذه المرحلة داخل مكة المكرمة فقط")
    private String city;

    @Size(max = 100, message = "الحي يجب ألا يتجاوز 100 حرف")
    private String district;

    @JsonDeserialize(using = StrictStringEnumDeserializer.class)
    private PropertyNeedsRequirement districtRequirement;

    @Positive(message = "الحد الأدنى للمساحة يجب أن يكون أكبر من صفر")
    @DecimalMin(value = "0.01", message = "المساحة يجب ألا تقل عن 0.01 متر مربع")
    @DecimalMax(value = "9999999999.99", message = "المساحة تتجاوز الحد المسموح")
    @Digits(integer = 10, fraction = 2, message = "المساحة تقبل حتى 10 أرقام صحيحة ومنزلتين عشريتين")
    private BigDecimal minArea;

    @Positive(message = "الحد الأعلى للمساحة يجب أن يكون أكبر من صفر")
    @DecimalMin(value = "0.01", message = "المساحة يجب ألا تقل عن 0.01 متر مربع")
    @DecimalMax(value = "9999999999.99", message = "المساحة تتجاوز الحد المسموح")
    @Digits(integer = 10, fraction = 2, message = "المساحة تقبل حتى 10 أرقام صحيحة ومنزلتين عشريتين")
    private BigDecimal maxArea;

    @JsonDeserialize(using = StrictStringEnumDeserializer.class)
    private PropertyNeedsRequirement areaRequirement;
    @JsonDeserialize(using = StrictStringEnumDeserializer.class)
    private PropertySearchGoal searchGoal;

    /** Reject unsupported criteria only for this DTO, without changing legacy JSON handling. */
    @JsonAnySetter
    public void rejectUnsupportedField(String name, Object value) {
        throw new IllegalArgumentException("Unsupported property-needs field: " + name);
    }

    public ListingType getListingType() { return listingType; }
    public void setListingType(ListingType listingType) { this.listingType = listingType; }
    public RentalPeriod getRentalPeriod() { return rentalPeriod; }
    public void setRentalPeriod(RentalPeriod rentalPeriod) { this.rentalPeriod = rentalPeriod; }
    public PropertyType getType() { return type; }
    public void setType(PropertyType type) { this.type = type; }
    public BigDecimal getMaxBudget() { return maxBudget; }
    public void setMaxBudget(BigDecimal maxBudget) { this.maxBudget = maxBudget; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getDistrict() { return district; }
    public void setDistrict(String district) {
        this.district = district == null || district.isBlank() ? null : district.strip();
    }
    public PropertyNeedsRequirement getDistrictRequirement() { return districtRequirement; }
    public void setDistrictRequirement(PropertyNeedsRequirement districtRequirement) {
        this.districtRequirement = districtRequirement;
    }
    public BigDecimal getMinArea() { return minArea; }
    public void setMinArea(BigDecimal minArea) { this.minArea = minArea; }
    public BigDecimal getMaxArea() { return maxArea; }
    public void setMaxArea(BigDecimal maxArea) { this.maxArea = maxArea; }
    public PropertyNeedsRequirement getAreaRequirement() { return areaRequirement; }
    public void setAreaRequirement(PropertyNeedsRequirement areaRequirement) {
        this.areaRequirement = areaRequirement;
    }
    public PropertySearchGoal getSearchGoal() { return searchGoal; }
    public void setSearchGoal(PropertySearchGoal searchGoal) { this.searchGoal = searchGoal; }
}
