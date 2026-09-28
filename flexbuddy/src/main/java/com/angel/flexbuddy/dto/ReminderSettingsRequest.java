package com.angel.flexbuddy.dto;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.validation.ValidTimeZone;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record ReminderSettingsRequest(
        @NotBlank @ValidTimeZone String timeZone,
        Integer remindBeforeMinutes,
        boolean remindConfirm,
        /** Optional; when absent the saved choice is kept. */
        Boolean remindMiles,
        /** Optional; when absent the saved cutoff is kept. */
        @Min(value = 0, message = "The forfeit cutoff must be between 0 and 720 minutes.")
        @Max(value = AppUser.MAX_FORFEIT_CUTOFF_MINUTES, message = "The forfeit cutoff must be between 0 and 720 minutes.")
        Integer forfeitCutoffMinutes
) {
    public ReminderSettingsRequest(String timeZone, Integer remindBeforeMinutes, boolean remindConfirm,
            Boolean remindMiles) {
        this(timeZone, remindBeforeMinutes, remindConfirm, remindMiles, null);
    }

    @AssertTrue(message = "Reminder lead time must be 30, 60, 120, or 720 minutes.")
    public boolean isRemindBeforeMinutesSupported() {
        return remindBeforeMinutes == null || AppUser.REMINDER_LEAD_MINUTES.contains(remindBeforeMinutes);
    }
}
