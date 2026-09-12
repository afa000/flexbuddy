package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.util.Map;

import com.angel.flexbuddy.model.ExpenseCategory;

public record NetEarningsResult(
        int shifts, BigDecimal grossEarnings, int minutesWorked, BigDecimal miles,
        BigDecimal mileageCost, Map<ExpenseCategory, BigDecimal> expenseTotals,
        BigDecimal vehicleCost, BigDecimal outOfPocketExpenses, BigDecimal totalDeductions,
        BigDecimal cashSpent, BigDecimal netEarnings, BigDecimal grossHourlyRate,
        BigDecimal netHourlyRate, BigDecimal netPerShift, BigDecimal earningsPerMile,
        BigDecimal netMargin
) {}
