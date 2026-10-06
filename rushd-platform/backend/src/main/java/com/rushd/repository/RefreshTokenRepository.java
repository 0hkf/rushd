package com.rushd.repository;
import com.rushd.entity.RefreshToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByTokenHash(String hash);
    @Query("select t.session.id from RefreshToken t where t.tokenHash = :hash")
    Optional<UUID> findSessionIdByHash(@Param("hash") String hash);
}
