package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record ParsedShiftData(
        ParsedField<String> station,
        ParsedField<LocalDate> date,
        ParsedField<LocalTime> startTime,
        ParsedField<LocalTime> endTime,
        ParsedField<BigDecimal> basePay,
        ParsedField<BigDecimal> tips,
        List<ImportWarning> warnings
) {
    public ParsedShiftData {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }

    public ParsedShiftData withDate(ParsedField<LocalDate> replacement) {
        return new ParsedShiftData(station, replacement, startTime, endTime, basePay, tips, warnings);
    }

    public ParsedShiftData withWarnings(List<ImportWarning> replacement) {
        return new ParsedShiftData(station, date, startTime, endTime, basePay, tips, replacement);
    }
}
