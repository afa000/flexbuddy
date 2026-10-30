package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.GoalBasis;
import com.angel.flexbuddy.model.VehicleCostMethod;

public record AccountSettingsResponse(VehicleCostMethod vehicleCostMethod, BigDecimal mileageRate,
        BigDecimal defaultMileageRate, LocalDate defaultMileageRateSince, String timeZone, Integer remindBeforeMinutes,
        boolean remindConfirm, boolean remindMiles, int forfeitCutoffMinutes, BigDecimal weeklyGoal,
        BigDecimal monthlyGoal, GoalBasis goalBasis, List<DayOfWeek> payoutDays, int payoutLagDays,
        BigDecimal taxSetAsidePercent, String calendarFeedPath, boolean remindTax, boolean askMissingMiles,
        String language, boolean customMileageRate) {

    // The shorter constructors below treat the rate they are given as the account's own, one rate for every date.

    /** Where each block's mileage rate comes from: the account's own rate, or the IRS rate for the block's date. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public com.angel.flexbuddy.service.MileageRates mileageRates() {
        return customMileageRate ? com.angel.flexbuddy.service.MileageRates.fixed(mileageRate)
                : com.angel.flexbuddy.service.MileageRates.irs();
    }

    /** The settings before the driver could choose a language; null follows the device. */
    public AccountSettingsResponse(VehicleCostMethod vehicleCostMethod, BigDecimal mileageRate,
            BigDecimal defaultMileageRate, LocalDate defaultMileageRateSince, String timeZone, Integer remindBeforeMinutes,
            boolean remindConfirm, boolean remindMiles, int forfeitCutoffMinutes, BigDecimal weeklyGoal,
            BigDecimal monthlyGoal, GoalBasis goalBasis, List<DayOfWeek> payoutDays, int payoutLagDays,
            BigDecimal taxSetAsidePercent, String calendarFeedPath, boolean remindTax, boolean askMissingMiles) {
        this(vehicleCostMethod, mileageRate, defaultMileageRate, defaultMileageRateSince, timeZone, remindBeforeMinutes,
                remindConfirm, remindMiles, forfeitCutoffMinutes, weeklyGoal, monthlyGoal, goalBasis, payoutDays,
                payoutLagDays, taxSetAsidePercent, calendarFeedPath, remindTax, askMissingMiles, null, true);
    }

    /** The settings before the missing-miles prompts could be turned off. */
    public AccountSettingsResponse(VehicleCostMethod vehicleCostMethod, BigDecimal mileageRate,
            BigDecimal defaultMileageRate, LocalDate defaultMileageRateSince, String timeZone, Integer remindBeforeMinutes,
            boolean remindConfirm, boolean remindMiles, int forfeitCutoffMinutes, BigDecimal weeklyGoal,
            BigDecimal monthlyGoal, GoalBasis goalBasis, List<DayOfWeek> payoutDays, int payoutLagDays,
            BigDecimal taxSetAsidePercent, String calendarFeedPath, boolean remindTax) {
        this(vehicleCostMethod, mileageRate, defaultMileageRate, defaultMileageRateSince, timeZone, remindBeforeMinutes,
                remindConfirm, remindMiles, forfeitCutoffMinutes, weeklyGoal, monthlyGoal, goalBasis, payoutDays,
                payoutLagDays, taxSetAsidePercent, calendarFeedPath, remindTax, true, null, true);
    }

    /** The settings before tax due-date reminders existed. */
    public AccountSettingsResponse(VehicleCostMethod vehicleCostMethod, BigDecimal mileageRate,
            BigDecimal defaultMileageRate, LocalDate defaultMileageRateSince, String timeZone, Integer remindBeforeMinutes,
            boolean remindConfirm, boolean remindMiles, int forfeitCutoffMinutes, BigDecimal weeklyGoal,
            BigDecimal monthlyGoal, GoalBasis goalBasis, List<DayOfWeek> payoutDays, int payoutLagDays,
            BigDecimal taxSetAsidePercent, String calendarFeedPath) {
        this(vehicleCostMethod, mileageRate, defaultMileageRate, defaultMileageRateSince, timeZone, remindBeforeMinutes,
                remindConfirm, remindMiles, forfeitCutoffMinutes, weeklyGoal, monthlyGoal, goalBasis, payoutDays,
                payoutLagDays, taxSetAsidePercent, calendarFeedPath, false, true, null, true);
    }

    public AccountSettingsResponse(VehicleCostMethod vehicleCostMethod, BigDecimal mileageRate,
            BigDecimal defaultMileageRate, LocalDate defaultMileageRateSince) {
        this(vehicleCostMethod, mileageRate, defaultMileageRate, defaultMileageRateSince, AppUser.DEFAULT_TIME_ZONE,
                null, false, false, AppUser.DEFAULT_FORFEIT_CUTOFF_MINUTES, null, null, GoalBasis.GROSS,
                List.of(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), AppUser.DEFAULT_PAYOUT_LAG_DAYS, null, null, false, true, null, true);
    }
}
