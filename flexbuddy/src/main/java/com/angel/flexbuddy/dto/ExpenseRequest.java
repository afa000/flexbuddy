package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import com.angel.flexbuddy.model.ExpenseCategory;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Digits;

public record ExpenseRequest(
        @NotNull LocalDate date,
        @NotNull ExpenseCategory category,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @Size(max = 255) String note,
        Long shiftId
) {}
