package com.rushd.repository;

import com.rushd.entity.Property;
import com.rushd.entity.PropertyStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface PropertyRepository extends JpaRepository<Property, Long> {

    List<Property> findBySellerId(Long sellerId);

    List<Property> findByStatus(PropertyStatus status);
}
