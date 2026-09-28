package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

import com.angel.flexbuddy.model.GoalBasis;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Earnings goals; a null amount turns that goal off. */
public record GoalSettingsRequest(
        @Positive(message = "A weekly goal must be more than zero.") @Digits(integer = 8, fraction = 2) BigDecimal weeklyGoal,
        @Positive(message = "A monthly goal must be more than zero.") @Digits(integer = 8, fraction = 2) BigDecimal monthlyGoal,
        @NotNull GoalBasis goalBasis
) {
}
