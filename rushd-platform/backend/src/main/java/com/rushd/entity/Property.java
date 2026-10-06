package com.rushd.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "properties",
        indexes = {
                @Index(name = "idx_properties_seller_id", columnList = "seller_id"),
                @Index(name = "idx_properties_status", columnList = "status"),
                @Index(name = "idx_properties_city", columnList = "city"),
                @Index(name = "idx_properties_district", columnList = "district"),
                @Index(name = "idx_properties_google_place_id", columnList = "google_place_id")
        }
)
public class Property {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "seller_id", nullable = false)
    private User publisher;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "listing_type", nullable = false, length = 10)
    private ListingType listingType = ListingType.SALE;

    @Enumerated(EnumType.STRING)
    @Column(name = "rental_period", length = 10)
    private RentalPeriod rentalPeriod;

    @Column(nullable = false)
    private String city;

    @Column(nullable = false)
    private String district;

    @Column(name = "google_place_id", length = 255)
    private String googlePlaceId;

    @Column(name = "formatted_address", length = 500)
    private String formattedAddress;

    @Column(length = 150)
    private String neighborhood;

    @Column(precision = 10, scale = 7)
    private BigDecimal latitude;

    @Column(precision = 10, scale = 7)
    private BigDecimal longitude;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal area;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(name = "street_width", precision = 10, scale = 2)
    private BigDecimal streetWidth;

    @Enumerated(EnumType.STRING)
    private PropertyFacade facade = PropertyFacade.UNKNOWN;

    @Enumerated(EnumType.STRING)
    private PropertyPurpose purpose;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PropertyStatus status = PropertyStatus.ACTIVE;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (this.status == null) {
            this.status = PropertyStatus.ACTIVE;
        }
        if (this.listingType == null) {
            this.listingType = ListingType.SALE;
        }
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getPublisher() {
        return publisher;
    }

    public void setPublisher(User publisher) {
        this.publisher = publisher;
    }

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

    public ListingType getListingType() {
        return listingType == null ? ListingType.SALE : listingType;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
