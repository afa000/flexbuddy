package com.angel.flexbuddy.repository;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import com.angel.flexbuddy.model.AppSignInChallenge;

public interface AppSignInChallengeRepository extends JpaRepository<AppSignInChallenge, Long> {
    Optional<AppSignInChallenge> findByChallengeHash(String hash);
    @Modifying(flushAutomatically = true)
    @Query("delete from AppSignInChallenge c where c.owner.id = :ownerId")
    int deleteAllByOwnerId(Long ownerId);
    @Modifying
    @Query("delete from AppSignInChallenge c where c.expiresAt <= :now")
    int deleteExpired(Instant now);
}
