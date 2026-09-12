package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

public record EarningsTotals(
        int shifts,
        BigDecimal basePay,
        BigDecimal tips,
        BigDecimal totalEarnings,
        int minutesWorked,
        BigDecimal hourlyRate
) {}
