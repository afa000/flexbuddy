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
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) implements Serializable {
}
