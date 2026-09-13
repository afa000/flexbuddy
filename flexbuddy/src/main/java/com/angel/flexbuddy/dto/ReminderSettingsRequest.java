package com.angel.flexbuddy.dto;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.validation.ValidTimeZone;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;

public record ReminderSettingsRequest(
        @NotBlank @ValidTimeZone String timeZone,
        Integer remindBeforeMinutes,
        boolean remindConfirm
) {
    @AssertTrue(message = "Reminder lead time must be 30, 60, 120, or 720 minutes.")
    public boolean isRemindBeforeMinutesSupported() {
        return remindBeforeMinutes == null || AppUser.REMINDER_LEAD_MINUTES.contains(remindBeforeMinutes);
    }
}
