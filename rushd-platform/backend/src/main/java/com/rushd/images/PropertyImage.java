package com.rushd.images;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "property_images", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"property_id", "upload_token"}),
        @UniqueConstraint(columnNames = {"property_id", "sort_order"})})
public class PropertyImage {
    public enum State { READY, DELETING, DELETED }
    public enum RightsBasis { OWNED, AUTHORIZED, LICENSED }
    @Id UUID id;
    @Column(name = "property_id", nullable = false) Long propertyId;
    @Column(name = "storage_key", nullable = false, unique = true) UUID storageKey;
    @Column(name = "upload_token", nullable = false) UUID uploadToken;
    @Column(nullable = false, length = 64) String fingerprint;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) State state;
    @Column(name = "sort_order") Integer sortOrder;
    @Column(name = "alt_text", length = 160) String altText;
    @Column(name = "display_width", nullable = false) int displayWidth;
    @Column(name = "display_height", nullable = false) int displayHeight;
    @Column(name = "thumbnail_width", nullable = false) int thumbnailWidth;
    @Column(name = "thumbnail_height", nullable = false) int thumbnailHeight;
    @Column(name = "display_bytes", nullable = false) long displayBytes;
    @Column(name = "thumbnail_bytes", nullable = false) long thumbnailBytes;
    @Column(name = "attested_by", nullable = false) Long attestedBy;
    @Enumerated(EnumType.STRING) @Column(name = "rights_basis", nullable = false, length = 16) RightsBasis rightsBasis;
    @Column(name = "source_reference", length = 500) String sourceReference;
    @Column(name = "created_at", nullable = false) Instant createdAt;
    @Column(name = "deleted_at") Instant deletedAt;
    protected PropertyImage() {}
}
