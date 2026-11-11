package com.rushd.favorites;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface Favorites extends JpaRepository<Favorite, FavoriteId> {
    @Query(value = "select f from Favorite f join fetch f.property where f.userId = :userId",
            countQuery = "select count(f) from Favorite f where f.userId = :userId")
    Page<Favorite> pageFor(@Param("userId") Long userId, Pageable pageable);
    @Query("select f.propertyId from Favorite f where f.userId = :userId and f.propertyId in :ids order by f.propertyId")
    List<Long> savedIds(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);
    @Modifying(flushAutomatically = true)
    @Query("delete from Favorite f where f.userId = :userId and f.propertyId = :propertyId")
    int removeFor(@Param("userId") Long userId, @Param("propertyId") Long propertyId);
}
