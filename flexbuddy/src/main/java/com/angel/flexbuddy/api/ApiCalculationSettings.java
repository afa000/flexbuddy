package com.angel.flexbuddy.api;

import java.util.List;

import com.angel.flexbuddy.dto.AccountSettingsResponse;

/** Only the inputs the phone needs for shared calculations; never the website's private calendar settings. */
public record ApiCalculationSettings(String timeZone, String vehicleCostMethod, String customMileageRate,
        String weeklyGoal, String monthlyGoal, String goalBasis, List<String> payoutDays, int payoutLagDays) {

    static ApiCalculationSettings of(AccountSettingsResponse settings) {
        return new ApiCalculationSettings(settings.timeZone(), ApiValues.code(settings.vehicleCostMethod()),
                settings.customMileageRate() ? ApiValues.decimal(settings.mileageRate()) : null,
                ApiValues.money(settings.weeklyGoal()), ApiValues.money(settings.monthlyGoal()),
                ApiValues.code(settings.goalBasis()),
                settings.payoutDays().stream().sorted().map(ApiValues::code).toList(), settings.payoutLagDays());
    }
}
