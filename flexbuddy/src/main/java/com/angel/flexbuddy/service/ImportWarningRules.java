package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ImportWarningRules {

    public ParsedShiftData apply(ParsedShiftData shift, int meanConfidence, ParseContext context) {
        List<ImportWarning> warnings = new ArrayList<>(shift.warnings());
        checkField("station", "Station", shift.station(), warnings);
        checkField("date", "Date", shift.date(), warnings);
        checkField("startTime", "Start time", shift.startTime(), warnings);
        checkField("endTime", "End time", shift.endTime(), warnings);
        checkField("basePay", "Base pay", shift.basePay(), warnings);
        if (meanConfidence < 55) {
            warnings.add(warning("POOR_IMAGE", WarningSeverity.WARNING, null,
                    "The screenshot was difficult to read. Check all imported values."));
        }
        checkDate(shift, context, warnings);
        checkTimeAndPay(shift, warnings);
        if (shift.basePay().value() != null && shift.tips().value() != null
                && shift.tips().value().compareTo(shift.basePay().value()) > 0) {
            warnings.add(warning("TIPS_EXCEED_BASE", WarningSeverity.WARNING, "tips",
                    "Tips are higher than base pay. Confirm that the values were read correctly."));
        }
        return shift.withWarnings(warnings.stream().distinct().toList());
    }

    private void checkField(String field, String label, ParsedField<?> value, List<ImportWarning> warnings) {
        if (value.level() == ConfidenceLevel.MISSING) {
            warnings.add(warning("MISSING_FIELD", WarningSeverity.ERROR, field,
                    label + " could not be read from the screenshot."));
        } else if (value.level() == ConfidenceLevel.LOW) {
            warnings.add(warning("LOW_CONFIDENCE", WarningSeverity.WARNING, field,
                    label + " may have been read incorrectly."));
        } else if (value.level() == ConfidenceLevel.MEDIUM) {
            warnings.add(warning("MEDIUM_CONFIDENCE", WarningSeverity.INFO, field,
                    "Please confirm the imported " + label.toLowerCase() + "."));
        }
    }

    private void checkDate(ParsedShiftData shift, ParseContext context, List<ImportWarning> warnings) {
        if (shift.date().value() == null) return;
        if (shift.date().value().isAfter(context.today().plusDays(14))) {
            warnings.add(warning("FUTURE_DATE", WarningSeverity.WARNING, "date",
                    "This date is more than two weeks in the future."));
        }
        if (shift.date().value().isBefore(context.today().minusDays(400))) {
            warnings.add(warning("OLD_DATE", WarningSeverity.WARNING, "date",
                    "This date is more than 400 days old."));
        }
    }

    private void checkTimeAndPay(ParsedShiftData shift, List<ImportWarning> warnings) {
        if (shift.startTime().value() == null || shift.endTime().value() == null) return;
        LocalDate date = shift.date().value() == null ? LocalDate.of(2000, 1, 1) : shift.date().value();
        LocalDateTime start = LocalDateTime.of(date, shift.startTime().value());
        LocalDateTime end = LocalDateTime.of(date, shift.endTime().value());
        if (!end.isAfter(start)) {
            end = end.plusDays(1);
            warnings.add(warning("OVERNIGHT", WarningSeverity.INFO, "endTime",
                    "This shift appears to end on the following day."));
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes > 600) {
            warnings.add(warning("LONG_SHIFT", WarningSeverity.WARNING, "endTime",
                    "This shift is longer than 10 hours."));
        } else if (minutes < 30) {
            warnings.add(warning("SHORT_SHIFT", WarningSeverity.WARNING, "endTime",
                    "This shift is shorter than 30 minutes."));
        }

        BigDecimal basePay = shift.basePay().value();
        if (basePay == null) return;
        boolean amountOutsideRange = basePay.compareTo(new BigDecimal("10")) < 0
                || basePay.compareTo(new BigDecimal("600")) > 0;
        BigDecimal hourly = minutes == 0 ? BigDecimal.ZERO : basePay.multiply(BigDecimal.valueOf(60))
                .divide(BigDecimal.valueOf(minutes), 2, RoundingMode.HALF_UP);
        boolean hourlyOutsideRange = hourly.compareTo(new BigDecimal("10")) < 0
                || hourly.compareTo(new BigDecimal("90")) > 0;
        if (amountOutsideRange || hourlyOutsideRange) {
            warnings.add(warning("PAY_OUT_OF_RANGE", WarningSeverity.WARNING, "basePay",
                    "The imported pay or hourly rate is outside the usual range."));
        }
    }

    private ImportWarning warning(String code, WarningSeverity severity, String field, String message) {
        return new ImportWarning(code, severity, field, message);
    }
}
