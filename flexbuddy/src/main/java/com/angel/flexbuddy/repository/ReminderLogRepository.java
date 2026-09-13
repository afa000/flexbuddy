package com.angel.flexbuddy.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.angel.flexbuddy.model.ReminderLog;

public interface ReminderLogRepository extends JpaRepository<ReminderLog, ReminderLog.Key> {
}
