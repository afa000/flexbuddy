package com.angel.flexbuddy.api;

import java.util.List;

import com.angel.flexbuddy.dto.AccountSettingsResponse;

/**
 * The settings the app's Settings screen shows and changes (FB-059). Never the private calendar feed link, the IRS
 * rate, or sign-in and two-step details.
 */
public record ApiAccountSettings(String language, String timeZone, String vehicleCostMethod,
        String customMileageRate, String weeklyGoal, String monthlyGoal, String goalBasis, List<String> payoutDays,
        int payoutLagDays, String taxSetAsidePercent, boolean remindTax, Integer remindBeforeMinutes,
        boolean remindConfirm, boolean remindMiles, boolean askMissingMiles, int forfeitCutoffMinutes) {

    static ApiAccountSettings of(AccountSettingsResponse settings) {
        // The fields shared with GET /api/v1/calculation-settings come from its mapping, so the two never disagree.
        ApiCalculationSettings shared = ApiCalculationSettings.of(settings);
        return new ApiAccountSettings(settings.language(), shared.timeZone(), shared.vehicleCostMethod(),
                shared.customMileageRate(), shared.weeklyGoal(), shared.monthlyGoal(), shared.goalBasis(),
                shared.payoutDays(), shared.payoutLagDays(), ApiValues.decimal(settings.taxSetAsidePercent()),
                settings.remindTax(), settings.remindBeforeMinutes(), settings.remindConfirm(), settings.remindMiles(),
                settings.askMissingMiles(), settings.forfeitCutoffMinutes());
    }
}
