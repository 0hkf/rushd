package com.rushd.images;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface PropertyImages extends JpaRepository<PropertyImage, UUID> {
    List<PropertyImage> findByPropertyId(Long propertyId);
    List<PropertyImage> findByStateAndDeletedAtBefore(PropertyImage.State state, java.time.Instant before);
    List<PropertyImage> findByPropertyIdAndStateOrderBySortOrder(Long propertyId, PropertyImage.State state);
    Optional<PropertyImage> findByPropertyIdAndUploadToken(Long propertyId, UUID uploadToken);
    Optional<PropertyImage> findByIdAndPropertyId(UUID id, Long propertyId);
    Optional<PropertyImage> findByStorageKey(UUID key);
    long countByPropertyIdAndStateNot(Long propertyId, PropertyImage.State state);
    @Query("select i from PropertyImage i, PropertyImageCollection c where c.propertyId in :ids " +
            "and i.id = c.primaryImageId and i.propertyId = c.propertyId and i.state = :state")
    List<PropertyImage> findPrimaries(@Param("ids") Collection<Long> ids, @Param("state") PropertyImage.State state);
    @Query("select i.storageKey from PropertyImage i where i.state <> :state")
    List<UUID> liveStorageKeys(@Param("state") PropertyImage.State state);
}
