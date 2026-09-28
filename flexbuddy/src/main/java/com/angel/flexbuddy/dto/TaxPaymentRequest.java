package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record TaxPaymentRequest(
        @NotNull @Min(2000) @Max(2100) Integer taxYear,
        @Min(1) @Max(4) Integer quarter,
        @NotNull LocalDate paidOn,
        @NotNull @Positive @Digits(integer = 10, fraction = 2) BigDecimal amount,
        @Size(max = 255) String note
) {
}
