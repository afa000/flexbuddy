package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ParsedShiftData(
        String station,
        LocalDate date,
        LocalTime startTime,
        LocalTime endTime,
        BigDecimal basePay,
        BigDecimal tips,
        List<String> warnings
) {
    public ParsedShiftData {
        warnings = List.copyOf(warnings);
    }
}
