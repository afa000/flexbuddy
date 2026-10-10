package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.angel.flexbuddy.i18n.Messages;

@Component
public class ImportWarningRules {

    public ParsedShiftData apply(ParsedShiftData shift, int meanConfidence, ParseContext context) {
        return apply(shift, meanConfidence, context, false);
    }

    /** A future date is expected for a scheduled block, so FUTURE_DATE is informational there. */
    public ParsedShiftData apply(ParsedShiftData shift, int meanConfidence, ParseContext context,
            boolean scheduled) {
        List<ImportWarning> warnings = new ArrayList<>(shift.warnings());
        checkField("station", shift.station(), warnings, "import.warning.stationMissing",
                "import.warning.stationLow", "import.warning.stationConfirm");
        checkField("date", shift.date(), warnings, "import.warning.dateMissing",
                "import.warning.dateLow", "import.warning.dateConfirm");
        checkField("startTime", shift.startTime(), warnings, "import.warning.startTimeMissing",
                "import.warning.startTimeLow", "import.warning.startTimeConfirm");
        checkField("endTime", shift.endTime(), warnings, "import.warning.endTimeMissing",
                "import.warning.endTimeLow", "import.warning.endTimeConfirm");
        checkField("basePay", shift.basePay(), warnings, "import.warning.basePayMissing",
                "import.warning.basePayLow", "import.warning.basePayConfirm");
        if (meanConfidence < 55) {
            warnings.add(warning("POOR_IMAGE", WarningSeverity.WARNING, null,
                    "import.warning.poorImage"));
        }
        checkDate(shift, context, scheduled, warnings);
        checkTimeAndPay(shift, warnings);
        if (shift.basePay().value() != null && shift.tips().value() != null
                && shift.tips().value().compareTo(shift.basePay().value()) > 0) {
            warnings.add(warning("TIPS_EXCEED_BASE", WarningSeverity.WARNING, "tips",
                    "import.warning.tipsExceedBase"));
        }
        return shift.withWarnings(warnings.stream().distinct().toList());
    }

    private void checkField(String field, ParsedField<?> value, List<ImportWarning> warnings, String missingKey,
            String lowKey, String confirmKey) {
        if (value.level() == ConfidenceLevel.MISSING) {
            warnings.add(warning("MISSING_FIELD", WarningSeverity.ERROR, field, missingKey));
        } else if (value.level() == ConfidenceLevel.LOW) {
            warnings.add(warning("LOW_CONFIDENCE", WarningSeverity.WARNING, field, lowKey));
        } else if (value.level() == ConfidenceLevel.MEDIUM) {
            warnings.add(warning("MEDIUM_CONFIDENCE", WarningSeverity.INFO, field, confirmKey));
        }
    }

    private void checkDate(ParsedShiftData shift, ParseContext context, boolean scheduled,
            List<ImportWarning> warnings) {
        if (shift.date().value() == null) return;
        if (shift.date().value().isAfter(context.today().plusDays(14))) {
            warnings.add(scheduled
                    ? warning("FUTURE_DATE", WarningSeverity.INFO, "date",
                            "import.warning.scheduledFar")
                    : warning("FUTURE_DATE", WarningSeverity.WARNING, "date",
                            "import.warning.dateFar"));
        }
        if (shift.date().value().isBefore(context.today().minusDays(400))) {
            warnings.add(warning("OLD_DATE", WarningSeverity.WARNING, "date",
                    "import.warning.dateOld"));
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
                    "import.warning.overnight"));
        }
        long minutes = Duration.between(start, end).toMinutes();
        if (minutes > 600) {
            warnings.add(warning("LONG_SHIFT", WarningSeverity.WARNING, "endTime",
                    "import.warning.longShift"));
        } else if (minutes < 30) {
            warnings.add(warning("SHORT_SHIFT", WarningSeverity.WARNING, "endTime",
                    "import.warning.shortShift"));
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
                    "import.warning.payOutOfRange"));
        }
    }

    /** Warnings carry the text for the language of the request being served. */
    private ImportWarning warning(String code, WarningSeverity severity, String field, String messageKey) {
        return new ImportWarning(code, severity, field, Messages.current(messageKey));
    }
}
