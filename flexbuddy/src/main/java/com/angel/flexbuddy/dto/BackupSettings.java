package com.angel.flexbuddy.dto;

import java.io.Serializable;

public record BackupSettings(String vehicleCostMethod, String mileageRate, String timeZone,
        Integer remindBeforeMinutes, Boolean remindConfirm, Boolean remindMiles, Integer forfeitCutoffMinutes,
        String weeklyGoal, String monthlyGoal, String goalBasis, String payoutDays, Integer payoutLagDays)
        implements Serializable {

    public BackupSettings(String vehicleCostMethod, String mileageRate) {
        this(vehicleCostMethod, mileageRate, null, null, null, null, null, null, null, null, null, null);
    }
}
