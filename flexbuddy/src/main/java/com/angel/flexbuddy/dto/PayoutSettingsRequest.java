package com.angel.flexbuddy.dto;

import java.time.DayOfWeek;
import java.util.Set;

import com.angel.flexbuddy.model.AppUser;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

public record PayoutSettingsRequest(
        @NotEmpty(message = "Choose at least one payout day.") Set<DayOfWeek> payoutDays,
        @Min(value = 0, message = "The payout lag must be between 0 and 14 days.")
        @Max(value = AppUser.MAX_PAYOUT_LAG_DAYS, message = "The payout lag must be between 0 and 14 days.")
        int payoutLagDays
) {
}
