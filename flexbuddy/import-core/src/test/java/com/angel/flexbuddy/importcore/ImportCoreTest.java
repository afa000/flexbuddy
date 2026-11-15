package com.angel.flexbuddy.importcore;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ImportCoreTest {
    private final ShiftScreenshotParser parser = new ShiftScreenshotParser();
    private final ParseContext context = new ParseContext(LocalDate.of(2026, 9, 11), null);

    @ParameterizedTest
    @ValueSource(strings = {
            "Total $90.50\nBase $72.00\nTips $18.50",
            "Total $90.50\nPago base $72.00\nPropinas $18.50",
            "Total\n$90.50\nBase\n$72.00\nTips\n$18.50",
            "Total 90,50 GBP\nBase 72,00 GBP\nTips 18,50 GBP"
    })
    void keepsBasePaySeparateFromTipsAndTotal(String text) {
        var parsed = parser.parse(text, 2026, context);
        assertEquals(0, new BigDecimal("72.00").compareTo(parsed.basePay().value()));
        assertEquals(0, new BigDecimal("18.50").compareTo(parsed.tips().value()));
    }

    @Test
    void aTotalNeverFillsAnUnreadableBaseAmount() {
        var result = parser.parse("Base unreadable\nTotal $90.50\nTips $18.50", 2026, context);
        assertNull(result.basePay().value());
        assertEquals(ConfidenceLevel.MISSING, result.basePay().level());
        assertTrue(result.warnings().contains(new ImportWarning("MISSING_FIELD", WarningSeverity.ERROR,
                "basePay", "import.warning.basePayMissing")));
    }

    @Test
    void returnsOrderedKeysForMissingFieldsWithoutAnyTranslationService() {
        var result = parser.parse("Schedule Details", 2026, context);
        assertEquals(List.of("station", "date", "startTime", "endTime", "basePay"),
                result.warnings().stream().map(ImportWarning::field).toList());
        assertTrue(result.warnings().stream().allMatch(w -> w.code().equals("MISSING_FIELD")
                && w.severity() == WarningSeverity.ERROR && w.messageKey().startsWith("import.warning.")));
        assertEquals(BigDecimal.ZERO, result.tips().value());
    }

    @Test
    void infersThePreviousYearOnlyFromTheExplicitContextAndKeepsItsWarningFirst() {
        var january = new ParseContext(LocalDate.of(2027, 1, 5), null);
        var result = parser.parse("Monday, 12/28\n22:00 - 02:00\nBase $80", 2027, january);
        assertEquals(LocalDate.of(2026, 12, 28), result.date().value());
        assertEquals(new ImportWarning("YEAR_ROLLOVER", WarningSeverity.INFO, "date",
                "import.warning.yearRollover"), result.warnings().getFirst());
        assertTrue(result.warnings().stream().anyMatch(w -> w.messageKey().equals("import.warning.overnight")));
        var summer = new ParseContext(LocalDate.of(2027, 7, 5), null);
        assertEquals(LocalDate.of(2027, 12, 28), parser.parse("Monday, 12/28", 2027, summer).date().value());
    }

    @ParameterizedTest
    @CsvSource({"59,LOW", "60,MEDIUM", "79,MEDIUM", "80,HIGH"})
    void retainsFieldConfidenceAndSource(int confidence, ConfidenceLevel expected) {
        var result = parser.parse(List.of(new OcrLine("Depot (VEA7)", confidence, 1, 2, 3, 4, 8)), 2026, context);
        assertEquals(expected, result.station().level());
        assertEquals(confidence, result.station().confidence());
        assertEquals(8, result.station().lineIndex());
    }

    @Test
    void keepsTheWeakerLabelConfidenceForAWrappedAmount() {
        var result = parser.parse(List.of(new OcrLine("Base", 50, 0, 0, 0, 0, 0),
                new OcrLine("$72.00", 95, 0, 0, 0, 0, 1)), 2026, context);
        assertEquals(50, result.basePay().confidence());
        assertEquals(1, result.basePay().lineIndex());
        assertEquals(ConfidenceLevel.LOW, result.basePay().level());
    }

    @Test
    void scheduledFutureDatesRemainInformationalAndRepeatedRulesDoNotDuplicateWarnings() {
        var future = parser.parse(List.of(new OcrLine("Sunday, 10/4", 95, 0, 0, 0, 0, 0)), 2026, context);
        var rules = new ImportWarningRules();
        var result = rules.apply(future, 54, context, true);
        assertTrue(result.warnings().contains(new ImportWarning("FUTURE_DATE", WarningSeverity.INFO,
                "date", "import.warning.scheduledFar")));
        assertTrue(result.warnings().stream().anyMatch(w -> w.code().equals("POOR_IMAGE")));
        assertEquals(result, rules.apply(result, 54, context, true));
        assertTrue(rules.apply(future, 55, context).warnings().contains(new ImportWarning("FUTURE_DATE",
                WarningSeverity.WARNING, "date", "import.warning.dateFar")));
    }

    @Test
    void normalizesOcrInputsAndTakesImmutableSnapshots() {
        var source = new ArrayList<>(List.of(new OcrLine("  abc  ", 110, 1, 2, 3, 4, 0),
                new OcrLine("x", -1, 0, 0, 0, 0, 1)));
        var result = OcrResult.fromLines(source);
        source.clear();
        assertEquals(2, result.lines().size());
        assertEquals(75, result.meanConfidence());
        assertEquals("abc", result.lines().getFirst().text());
        assertThrows(UnsupportedOperationException.class, () -> result.lines().clear());
        var parsed = parser.parse("", 2026, context);
        assertThrows(UnsupportedOperationException.class, () -> parsed.warnings().clear());
    }

    @Test
    void recognizesScreenshotLanguageIndependentlyOfTheDevicesLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            var result = parser.parse("domingo, 6/9\nInicio\n9:00\nTerminar en\n10:00", 2026, context);
            assertEquals(LocalDate.of(2026, 9, 6), result.date().value());
            assertEquals(LocalTime.of(9, 0), result.startTime().value());
        } finally {
            Locale.setDefault(previous);
        }
    }
}
