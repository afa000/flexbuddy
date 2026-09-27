package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** A block offer as the Flex app shows it; expectedTips replaces the station's tip average when given. */
public record BlockEvaluationRequest(
        @NotBlank @Size(max = 255, message = "Station must be 255 characters or fewer.") String station,
        @NotNull @DecimalMin(value = "0.5", message = "Hours must be at least 0.5.")
        @DecimalMax(value = "12", message = "Hours must be 12 or fewer.") @Digits(integer = 2, fraction = 2) BigDecimal hours,
        @NotNull @Positive @Digits(integer = 7, fraction = 2) BigDecimal offeredPay,
        @PositiveOrZero @Digits(integer = 7, fraction = 2) BigDecimal expectedTips
) {
}
