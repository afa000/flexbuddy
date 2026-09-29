package com.angel.flexbuddy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.angel.flexbuddy.model.TaxReminderLog;

public interface TaxReminderLogRepository extends JpaRepository<TaxReminderLog, TaxReminderLog.Key> {

    @Modifying
    @Query("delete from TaxReminderLog log where log.ownerId = :ownerId")
    int deleteAllByOwnerId(@Param("ownerId") Long ownerId);
}
