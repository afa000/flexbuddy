package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record UpcomingWeek(LocalDate weekStart, LocalDate weekEnd, int shifts, int plannedMinutes,
        BigDecimal expectedPay) {}
