package com.angel.flexbuddy.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.angel.flexbuddy.model.ReminderLog;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, ReminderLog.Key> {

    @Modifying
    @Query(value = """
            delete from reminder_log
            where shift_id in (select id from shift where owner_id = :ownerId)
            """, nativeQuery = true)
    int deleteAllByOwnerId(@Param("ownerId") Long ownerId);
}
