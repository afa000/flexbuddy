package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.GoalBasis;
import com.angel.flexbuddy.model.VehicleCostMethod;

public record AccountSettingsResponse(VehicleCostMethod vehicleCostMethod, BigDecimal mileageRate,
        BigDecimal defaultMileageRate, int mileageRateYear, String timeZone, Integer remindBeforeMinutes,
        boolean remindConfirm, boolean remindMiles, int forfeitCutoffMinutes, BigDecimal weeklyGoal,
        BigDecimal monthlyGoal, GoalBasis goalBasis, String calendarFeedPath) {

    public AccountSettingsResponse(VehicleCostMethod vehicleCostMethod, BigDecimal mileageRate,
            BigDecimal defaultMileageRate, int mileageRateYear) {
        this(vehicleCostMethod, mileageRate, defaultMileageRate, mileageRateYear, AppUser.DEFAULT_TIME_ZONE,
                null, false, false, AppUser.DEFAULT_FORFEIT_CUTOFF_MINUTES, null, null, GoalBasis.GROSS, null);
    }
}
