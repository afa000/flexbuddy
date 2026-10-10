package com.angel.flexbuddy.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.angel.flexbuddy.model.RecoveryCode;

public interface RecoveryCodeRepository extends JpaRepository<RecoveryCode, Long> {

    List<RecoveryCode> findAllByOwnerIdAndUsedAtIsNull(Long ownerId);

    long countByOwnerIdAndUsedAtIsNull(Long ownerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from RecoveryCode c where c.owner.id = :ownerId")
    void deleteAllByOwnerId(@Param("ownerId") Long ownerId);
}
