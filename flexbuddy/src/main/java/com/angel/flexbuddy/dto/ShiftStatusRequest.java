package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.angel.flexbuddy.model.ShiftStatus;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record ShiftStatusRequest(
        @NotNull ShiftStatus status,
        @PositiveOrZero BigDecimal basePay,
        @PositiveOrZero BigDecimal tips,
        @PositiveOrZero @Digits(integer = 7, fraction = 1) BigDecimal miles,
        @Valid BlockDetailsRequest details,
        /** Optional; when present the change is refused with 409 if the shift was changed after this moment. */
        Instant expectedUpdatedAt
) {
    public ShiftStatusRequest(ShiftStatus status) {
        this(status, null, null, null);
    }

    public ShiftStatusRequest(ShiftStatus status, BigDecimal basePay, BigDecimal tips, BigDecimal miles) {
        this(status, basePay, tips, miles, null);
    }

    public ShiftStatusRequest(ShiftStatus status, BigDecimal basePay, BigDecimal tips, BigDecimal miles,
            BlockDetailsRequest details) {
        this(status, basePay, tips, miles, details, null);
    }
}
