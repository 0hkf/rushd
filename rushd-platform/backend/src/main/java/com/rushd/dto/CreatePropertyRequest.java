package com.rushd.dto;

import com.rushd.entity.PropertyFacade;
import com.rushd.entity.PropertyPurpose;
import com.rushd.entity.PropertyStatus;
import com.rushd.entity.PropertyType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public class CreatePropertyRequest {

    @NotBlank(message = "العنوان مطلوب")
    @Size(max = 150, message = "العنوان يجب ألا يتجاوز 150 حرفاً")
    private String title;

    @NotNull(message = "نوع العقار مطلوب")
    private PropertyType type;

    @NotBlank(message = "المدينة مطلوبة")
    @Size(max = 100, message = "المدينة يجب ألا تتجاوز 100 حرف")
    private String city;

    @NotBlank(message = "الحي مطلوب")
    @Size(max = 100, message = "الحي يجب ألا يتجاوز 100 حرف")
    private String district;

    @NotNull(message = "المساحة مطلوبة")
    @Positive(message = "المساحة يجب أن تكون أكبر من صفر")
    private BigDecimal area;

    @NotNull(message = "السعر مطلوب")
    @Positive(message = "السعر يجب أن يكون أكبر من صفر")
    private BigDecimal price;

    @Positive(message = "عرض الشارع يجب أن يكون أكبر من صفر")
    private BigDecimal streetWidth;

    private PropertyFacade facade;

    @NotNull(message = "غرض العقار مطلوب")
    private PropertyPurpose purpose;

    @Size(max = 3000, message = "الوصف يجب ألا يتجاوز 3000 حرف")
    private String description;

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
