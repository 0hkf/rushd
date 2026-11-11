package com.rushd.repository;

import com.rushd.entity.Property;
import com.rushd.entity.PropertyStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

public interface PropertyRepository extends JpaRepository<Property, Long>, JpaSpecificationExecutor<Property> {

    /** Serialize read/merge/write for one property, without locking ordinary public reads. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select property from Property property where property.id = :id")
    Optional<Property> findByIdForUpdate(@Param("id") Long id);

    List<Property> findByPublisherId(Long sellerId);

    List<Property> findByStatus(PropertyStatus status);
}
