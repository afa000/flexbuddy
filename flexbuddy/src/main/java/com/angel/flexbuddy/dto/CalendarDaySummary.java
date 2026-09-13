package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CalendarDaySummary(
        LocalDate date,
        int scheduled,
        int completed,
        int cancelled,
        int forfeited,
        BigDecimal expectedPay,
        BigDecimal earned,
        int minutes
) {}
