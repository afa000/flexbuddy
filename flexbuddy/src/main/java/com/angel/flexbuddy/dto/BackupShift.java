package com.angel.flexbuddy.dto;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record BackupShift(
        Long id,
        String station,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        String basePay,
        String tips,
        String miles,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        String status,
        Instant statusChangedAt
) implements Serializable {
    public BackupShift(Long id, String station, LocalDate date, LocalTime startTime, LocalTime endTime,
            String basePay, String tips, String miles, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this(id, station, date, startTime, endTime, basePay, tips, miles, createdAt, updatedAt, deletedAt, null, null);
    }

    public BackupShift(Long id, String station, LocalDate date, LocalTime startTime, LocalTime endTime,
            String basePay, String tips, Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this(id, station, date, startTime, endTime, basePay, tips, null, createdAt, updatedAt, deletedAt);
    }
}
