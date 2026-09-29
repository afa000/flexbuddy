package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** What landed for one payout. Zero is allowed, for a payout that never arrived. */
public record PayoutDepositRequest(
        @NotNull @DecimalMin("0.00") @DecimalMax("99999.99") @Digits(integer = 5, fraction = 2) BigDecimal amount,
        @Size(max = 255) String note
) {
}
