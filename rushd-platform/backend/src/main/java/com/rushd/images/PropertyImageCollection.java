package com.rushd.images;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "property_image_collections")
public class PropertyImageCollection {
    @Id @Column(name = "property_id") Long propertyId;
    @Column(nullable = false) long revision;
    @Column(name = "primary_image_id") UUID primaryImageId;
    protected PropertyImageCollection() {}
    PropertyImageCollection(Long propertyId) { this.propertyId = propertyId; }
}
