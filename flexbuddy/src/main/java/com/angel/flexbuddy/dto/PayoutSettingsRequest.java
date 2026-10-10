package com.angel.flexbuddy.dto;

import java.time.DayOfWeek;
import java.util.Set;

import com.angel.flexbuddy.model.AppUser;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;

public record PayoutSettingsRequest(
        @NotEmpty(message = "{validation.payoutDays.required}") Set<DayOfWeek> payoutDays,
        @Min(value = 0, message = "{validation.payoutLag.range}")
        @Max(value = AppUser.MAX_PAYOUT_LAG_DAYS, message = "{validation.payoutLag.range}")
        int payoutLagDays
) {
}
