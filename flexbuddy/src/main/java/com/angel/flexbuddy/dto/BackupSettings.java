package com.angel.flexbuddy.dto;

import java.io.Serializable;

public record BackupSettings(String vehicleCostMethod, String mileageRate, String timeZone,
        Integer remindBeforeMinutes, Boolean remindConfirm, Boolean remindMiles, Integer forfeitCutoffMinutes,
        String weeklyGoal, String monthlyGoal, String goalBasis, String payoutDays, Integer payoutLagDays,
        String taxSetAsidePercent, Boolean remindTax, Boolean askMissingMiles) implements Serializable {

    /** Backups made before the missing-miles prompts could be turned off; a missing choice keeps the account's current one. */
    public BackupSettings(String vehicleCostMethod, String mileageRate, String timeZone, Integer remindBeforeMinutes,
            Boolean remindConfirm, Boolean remindMiles, Integer forfeitCutoffMinutes, String weeklyGoal,
            String monthlyGoal, String goalBasis, String payoutDays, Integer payoutLagDays, String taxSetAsidePercent,
            Boolean remindTax) {
        this(vehicleCostMethod, mileageRate, timeZone, remindBeforeMinutes, remindConfirm, remindMiles,
                forfeitCutoffMinutes, weeklyGoal, monthlyGoal, goalBasis, payoutDays, payoutLagDays,
                taxSetAsidePercent, remindTax, null);
    }

    /** Backups made before tax due-date reminders existed; a missing choice keeps the account's current one. */
    public BackupSettings(String vehicleCostMethod, String mileageRate, String timeZone, Integer remindBeforeMinutes,
            Boolean remindConfirm, Boolean remindMiles, Integer forfeitCutoffMinutes, String weeklyGoal,
            String monthlyGoal, String goalBasis, String payoutDays, Integer payoutLagDays, String taxSetAsidePercent) {
        this(vehicleCostMethod, mileageRate, timeZone, remindBeforeMinutes, remindConfirm, remindMiles,
                forfeitCutoffMinutes, weeklyGoal, monthlyGoal, goalBasis, payoutDays, payoutLagDays,
                taxSetAsidePercent, null, null);
    }

    public BackupSettings(String vehicleCostMethod, String mileageRate) {
        this(vehicleCostMethod, mileageRate, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }
}
