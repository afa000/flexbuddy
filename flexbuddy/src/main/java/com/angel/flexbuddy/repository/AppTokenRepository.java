package com.angel.flexbuddy.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import com.angel.flexbuddy.model.AppToken;

public interface AppTokenRepository extends JpaRepository<AppToken, Long> {
    Optional<AppToken> findByTokenHash(String hash);
    @Modifying
    @Query("delete from AppToken t where t.owner.id = :ownerId")
    int deleteAllByOwnerId(Long ownerId);
    @Modifying
    @Query("delete from AppToken t where t.expiresAt <= :now")
    int deleteExpired(Instant now);
    @Modifying
    @Query("update AppToken t set t.lastUsedAt = :now, t.expiresAt = :expiry "
            + "where t.id = :id and t.lastUsedAt <= :cutoff and t.expiresAt > :now")
    int touch(Long id, Instant now, Instant expiry, Instant cutoff);
}
