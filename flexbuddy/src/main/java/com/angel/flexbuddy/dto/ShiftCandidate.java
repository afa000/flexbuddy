package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.service.ImportWarning;
import com.angel.flexbuddy.service.ParsedField;

public record ShiftCandidate(
        int index,
        ParsedField<String> station,
        ParsedField<LocalDate> date,
        ParsedField<LocalTime> startTime,
        ParsedField<LocalTime> endTime,
        ParsedField<BigDecimal> basePay,
        ParsedField<BigDecimal> tips,
        List<ImportWarning> warnings,
        List<DuplicateMatch> duplicates,
        List<Integer> sourceLineRange,
        ShiftStatus suggestedStatus
) {
    public ShiftCandidate {
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
        duplicates = duplicates == null ? List.of() : List.copyOf(duplicates);
        sourceLineRange = sourceLineRange == null ? List.of() : List.copyOf(sourceLineRange);
        suggestedStatus = suggestedStatus == null ? ShiftStatus.COMPLETED : suggestedStatus;
    }

    public ShiftCandidate(int index, ParsedField<String> station, ParsedField<LocalDate> date,
            ParsedField<LocalTime> startTime, ParsedField<LocalTime> endTime, ParsedField<BigDecimal> basePay,
            ParsedField<BigDecimal> tips, List<ImportWarning> warnings, List<DuplicateMatch> duplicates,
            List<Integer> sourceLineRange) {
        this(index, station, date, startTime, endTime, basePay, tips, warnings, duplicates, sourceLineRange, null);
    }
}
