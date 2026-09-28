package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

import java.time.DayOfWeek;
import java.util.List;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.GoalBasis;
import com.angel.flexbuddy.model.VehicleCostMethod;

public record AccountSettingsResponse(VehicleCostMethod vehicleCostMethod, BigDecimal mileageRate,
        BigDecimal defaultMileageRate, int mileageRateYear, String timeZone, Integer remindBeforeMinutes,
        boolean remindConfirm, boolean remindMiles, int forfeitCutoffMinutes, BigDecimal weeklyGoal,
        BigDecimal monthlyGoal, GoalBasis goalBasis, List<DayOfWeek> payoutDays, int payoutLagDays,
        BigDecimal taxSetAsidePercent, String calendarFeedPath) {

    public AccountSettingsResponse(VehicleCostMethod vehicleCostMethod, BigDecimal mileageRate,
            BigDecimal defaultMileageRate, int mileageRateYear) {
        this(vehicleCostMethod, mileageRate, defaultMileageRate, mileageRateYear, AppUser.DEFAULT_TIME_ZONE,
                null, false, false, AppUser.DEFAULT_FORFEIT_CUTOFF_MINUTES, null, null, GoalBasis.GROSS,
                List.of(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), AppUser.DEFAULT_PAYOUT_LAG_DAYS, null, null);
    }
}
