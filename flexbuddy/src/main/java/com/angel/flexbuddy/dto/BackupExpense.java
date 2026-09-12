package com.angel.flexbuddy.dto;

import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDate;

public record BackupExpense(Long id, LocalDate date, String category, String amount, String note,
        Long shiftBackupId, Instant createdAt, Instant updatedAt, Instant deletedAt) implements Serializable {}
