package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import com.angel.flexbuddy.model.ExpenseCategory;

public record ExpenseResponse(
        Long id,
        LocalDate date,
        ExpenseCategory category,
        BigDecimal amount,
        String note,
        Long shiftId,
        String station,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {}
