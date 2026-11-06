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
        Instant deletedAt,
        /** True when the linked block is in Recently deleted; the expense then counts as unlinked, by its date. */
        boolean shiftDeleted
) {}
