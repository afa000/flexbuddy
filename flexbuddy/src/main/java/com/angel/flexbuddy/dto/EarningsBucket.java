package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record EarningsBucket(
        String key,
        String label,
        LocalDate periodStart,
        LocalDate periodEnd,
        int shifts,
        BigDecimal basePay,
        BigDecimal tips,
        BigDecimal totalEarnings,
        int minutesWorked,
        BigDecimal hourlyRate
) {}
