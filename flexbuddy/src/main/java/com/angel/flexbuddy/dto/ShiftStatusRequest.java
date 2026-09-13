package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

import com.angel.flexbuddy.model.ShiftStatus;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ShiftStatusRequest(
        @NotNull ShiftStatus status,
        @PositiveOrZero BigDecimal basePay,
        @PositiveOrZero BigDecimal tips,
        @PositiveOrZero @Digits(integer = 7, fraction = 1) BigDecimal miles
) {
    public ShiftStatusRequest(ShiftStatus status) {
        this(status, null, null, null);
    }
}
