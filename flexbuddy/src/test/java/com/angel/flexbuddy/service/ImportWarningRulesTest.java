package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class ImportWarningRulesTest {

    private final ImportWarningRules rules = new ImportWarningRules();
    private final ParseContext context = new ParseContext(LocalDate.of(2026, 9, 11), null);

    @Test
    void apply_reportsMissingFieldsAndPoorImageQuality() {
        ParsedShiftData shift = new ParsedShiftData(
                ParsedField.missing(), ParsedField.missing(), ParsedField.missing(),
                ParsedField.missing(), ParsedField.missing(), ParsedField.defaulted(BigDecimal.ZERO), List.of());

        ParsedShiftData result = rules.apply(shift, 44, context);

        assertThat(result.warnings()).extracting(ImportWarning::code)
                .containsExactlyInAnyOrder(
                        "MISSING_FIELD", "MISSING_FIELD", "MISSING_FIELD",
                        "MISSING_FIELD", "MISSING_FIELD", "POOR_IMAGE");
        assertThat(result.warnings()).filteredOn(warning -> warning.code().equals("MISSING_FIELD"))
                .allMatch(warning -> warning.severity() == WarningSeverity.ERROR);
    }

    @Test
    void apply_reportsConfidenceAndPlausibilityConcerns() {
        ParsedShiftData shift = new ParsedShiftData(
                field("VEA7", 58, 0),
                field(LocalDate.of(2026, 9, 6), 72, 1),
                field(LocalTime.of(9, 0), 95, 2),
                field(LocalTime.of(9, 20), 95, 2),
                field(new BigDecimal("8.00"), 95, 3),
                field(new BigDecimal("12.00"), 95, 4),
                List.of());

        ParsedShiftData result = rules.apply(shift, 82, context);

        assertThat(result.warnings()).extracting(ImportWarning::code)
                .contains("LOW_CONFIDENCE", "MEDIUM_CONFIDENCE", "SHORT_SHIFT",
                        "PAY_OUT_OF_RANGE", "TIPS_EXCEED_BASE");
    }

    private <T> ParsedField<T> field(T value, int confidence, int line) {
        return ParsedField.found(value, new OcrLine("source", confidence, 0, 0, 100, 20, line));
    }
}
