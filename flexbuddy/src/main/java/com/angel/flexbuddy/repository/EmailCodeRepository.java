package com.angel.flexbuddy.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.angel.flexbuddy.model.EmailCode;
import com.angel.flexbuddy.model.EmailCodePurpose;

public interface EmailCodeRepository extends JpaRepository<EmailCode, Long> {

    Optional<EmailCode> findFirstByOwnerIdAndPurposeAndUsedAtIsNullOrderByCreatedAtDesc(Long ownerId,
            EmailCodePurpose purpose);

    /** Cancels every code of this kind for the account that has not been used yet. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update EmailCode c set c.usedAt = :at where c.owner.id = :ownerId and c.purpose = :purpose and c.usedAt is null")
    int markUnusedAsUsed(@Param("ownerId") Long ownerId, @Param("purpose") EmailCodePurpose purpose,
            @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EmailCode c where c.expiresAt < :cutoff")
    int deleteByExpiresAtBefore(@Param("cutoff") Instant cutoff);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from EmailCode c where c.owner.id = :ownerId")
    void deleteAllByOwnerId(@Param("ownerId") Long ownerId);
}
