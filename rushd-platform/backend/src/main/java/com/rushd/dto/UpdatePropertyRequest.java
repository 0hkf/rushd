package com.rushd.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.rushd.dto.deserialization.StrictStringEnumDeserializer;
import jakarta.validation.Valid;
import com.rushd.entity.ListingType;
import com.rushd.entity.RentalPeriod;
import jakarta.validation.constraints.AssertTrue;
import com.rushd.entity.PropertyFacade;
import com.rushd.entity.PropertyPurpose;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.PropertyType;
import com.rushd.validation.SafeDecimalPrecision;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class UpdatePropertyRequest {

    @Valid
    private PropertySpecificationsRequest specifications;

    public PropertySpecificationsRequest getSpecifications() { return specifications; }

    @JsonSetter(nulls = Nulls.FAIL)
    public void setSpecifications(PropertySpecificationsRequest specifications) { this.specifications = specifications; }

    @NotBlank(message = "العنوان مطلوب")
    @Size(max = 150, message = "العنوان يجب ألا يتجاوز 150 حرفاً")
    private String title;

    @NotNull(message = "نوع العقار مطلوب")
    @JsonDeserialize(using = StrictStringEnumDeserializer.class)
    private PropertyType type;

    @NotNull(message = "نوع العرض مطلوب")
    @JsonDeserialize(using = StrictStringEnumDeserializer.class)
    private ListingType listingType = ListingType.SALE;

    @JsonDeserialize(using = StrictStringEnumDeserializer.class)
    private RentalPeriod rentalPeriod;

    @JsonIgnore
    @AssertTrue(message = "حدد الفترة الشهرية أو السنوية للإيجار فقط")
    public boolean isRentalPricingValid() {
        if (listingType == null) {
            return true;
        }
        return listingType == ListingType.RENT ? rentalPeriod != null : rentalPeriod == null;
    }

    @JsonIgnore
    @AssertTrue(message = "حالة مباع لعروض البيع فقط. اختر حالة مناسبة للإيجار قبل الحفظ.")
    public boolean isListingStatusValid() {
        return listingType != ListingType.RENT || status != PropertyStatus.SOLD;
    }

    public ListingType getListingType() {
        return listingType;
    }

    public void setListingType(ListingType listingType) {
        this.listingType = listingType;
    }

    public RentalPeriod getRentalPeriod() {
        return rentalPeriod;
    }

    public void setRentalPeriod(RentalPeriod rentalPeriod) {
        this.rentalPeriod = rentalPeriod;
    }

    @NotBlank(message = "المدينة مطلوبة")
    @Size(max = 100, message = "المدينة يجب ألا تتجاوز 100 حرف")
    private String city;

    @NotBlank(message = "الحي مطلوب")
    @Size(max = 100, message = "الحي يجب ألا يتجاوز 100 حرف")
    private String district;

    @Size(max = 255, message = "معرّف موقع Google يجب ألا يتجاوز 255 حرفاً")
    private String googlePlaceId;

    @Size(max = 500, message = "العنوان المنسق يجب ألا يتجاوز 500 حرف")
    private String formattedAddress;

    @Size(max = 150, message = "اسم الحي يجب ألا يتجاوز 150 حرفاً")
    private String neighborhood;

    @DecimalMin(value = "-90", message = "خط العرض يجب ألا يقل عن -90")
    @DecimalMax(value = "90", message = "خط العرض يجب ألا يزيد عن 90")
    @SafeDecimalPrecision(integer = 3, fraction = 7, message = "خط العرض يقبل حتى 7 منازل عشرية و3 خانات صحيحة")
    private BigDecimal latitude;

    @DecimalMin(value = "-180", message = "خط الطول يجب ألا يقل عن -180")
    @DecimalMax(value = "180", message = "خط الطول يجب ألا يزيد عن 180")
    @SafeDecimalPrecision(integer = 3, fraction = 7, message = "خط الطول يقبل حتى 7 منازل عشرية و3 خانات صحيحة")
    private BigDecimal longitude;

    @NotNull(message = "المساحة مطلوبة")
    @Positive(message = "المساحة يجب أن تكون أكبر من صفر")
    @DecimalMin(value = "0.01", message = "المساحة يجب ألا تقل عن 0.01 متر مربع")
    @DecimalMax(value = "9999999999.99", message = "المساحة تتجاوز الحد المسموح")
    @SafeDecimalPrecision(integer = 10, fraction = 2, message = "المساحة تقبل حتى 10 خانات صحيحة ومنزلتين عشريتين")
    private BigDecimal area;

    @NotNull(message = "السعر مطلوب")
    @Positive(message = "السعر يجب أن يكون أكبر من صفر")
    @DecimalMin(value = "0.01", message = "السعر يجب ألا يقل عن 0.01 ريال")
    @DecimalMax(value = "99999999999999999.99", message = "السعر يتجاوز الحد المسموح")
    @SafeDecimalPrecision(integer = 17, fraction = 2, message = "السعر يقبل حتى 17 خانة صحيحة ومنزلتين عشريتين")
    private BigDecimal price;

    @Positive(message = "عرض الشارع يجب أن يكون أكبر من صفر")
    @DecimalMin(value = "0.01", message = "عرض الشارع يجب ألا يقل عن 0.01 متر")
    @DecimalMax(value = "99999999.99", message = "عرض الشارع يتجاوز الحد المسموح")
    @SafeDecimalPrecision(integer = 8, fraction = 2, message = "عرض الشارع يقبل حتى 8 خانات صحيحة ومنزلتين عشريتين")
    private BigDecimal streetWidth;

    @JsonDeserialize(using = StrictStringEnumDeserializer.class)
    private PropertyFacade facade;

    @NotNull(message = "غرض العقار مطلوب")
    @JsonDeserialize(using = StrictStringEnumDeserializer.class)
    private PropertyPurpose purpose;

    @Size(max = 3000, message = "الوصف يجب ألا يتجاوز 3000 حرف")
    private String description;

    @NotNull(message = "حالة العقار مطلوبة")
    @JsonDeserialize(using = StrictStringEnumDeserializer.class)
    private PropertyStatus status;

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public PropertyType getType() {
        return type;
    }

    public void setType(PropertyType type) {
        this.type = type;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getDistrict() {
        return district;
    }

    public void setDistrict(String district) {
        this.district = district;
    }

    public String getGooglePlaceId() {
        return googlePlaceId;
    }

    public void setGooglePlaceId(String googlePlaceId) {
        this.googlePlaceId = googlePlaceId;
    }

    public String getFormattedAddress() {
        return formattedAddress;
    }

    public void setFormattedAddress(String formattedAddress) {
        this.formattedAddress = formattedAddress;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public BigDecimal getLatitude() {
        return latitude;
    }

    public void setLatitude(BigDecimal latitude) {
        this.latitude = latitude;
    }

    public BigDecimal getLongitude() {
        return longitude;
    }

    public void setLongitude(BigDecimal longitude) {
        this.longitude = longitude;
    }

    public BigDecimal getArea() {
        return area;
    }

    public void setArea(BigDecimal area) {
        this.area = area;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public BigDecimal getStreetWidth() {
        return streetWidth;
    }

    public void setStreetWidth(BigDecimal streetWidth) {
        this.streetWidth = streetWidth;
    }

    public PropertyFacade getFacade() {
        return facade;
    }

    public void setFacade(PropertyFacade facade) {
        this.facade = facade;
    }

    public PropertyPurpose getPurpose() {
        return purpose;
    }

    public void setPurpose(PropertyPurpose purpose) {
        this.purpose = purpose;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public PropertyStatus getStatus() {
        return status;
    }

    public void setStatus(PropertyStatus status) {
        this.status = status;
    }
}
