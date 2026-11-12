package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ShiftScreenshotParserTest {

    private final ShiftScreenshotParser parser = new ShiftScreenshotParser();

    @Test
    void parse_usesBaseLabelsInsteadOfTotalRegardlessOfOrderOrLayout() {
        for (String text : java.util.List.of(
                "Total $90.50\nBase $72.00\nTips $18.50",
                "$90.50\nTips $18.50\nBase pay $72.00",
                "TOTAL: $90.50 BASE: $72.00 TIPS: $18.50",
                "Total\n$90.50\nBase\n$72.00\nTips\n$18.50",
                "Total $90.50\nBase\n$72.00 Tips $18.50",
                "Total $90.50\nPropinas\n$18.50 Base $72.00",
                "Total $90.50\nPago base $72.00\nPropinas $18.50",
                "Propinas $18.50\nImporte base $72.00\nTotal $90.50",
                "Total $90.50\nGanancias base\n\n$72.00\nPropinas\n$18.50")) {
            ParsedShiftData result = parser.parse(text, 2026);
            assertThat(result.basePay().value()).as(text).isEqualByComparingTo("72.00");
            assertThat(result.tips().value()).as(text).isEqualByComparingTo("18.50");
        }
    }

    @Test
    void parse_leavesBaseMissingRatherThanStealingTotalOrTips() {
        for (String text : java.util.List.of(
                "Total $90.50\nTips $18.50",
                "Base unreadable\nTotal $90.50\nTips $18.50",
                "Base\nTips $18.50\nTotal $90.50",
                "Base Total $90.50 Tips $18.50")) {
            ParsedShiftData result = parser.parse(text, 2026);
            assertThat(result.basePay().value()).as(text).isNull();
            assertThat(result.tips().value()).as(text).isEqualByComparingTo("18.50");
            assertThat(result.warnings()).anyMatch(warning ->
                    warning.code().equals("MISSING_FIELD") && warning.field().equals("basePay"));
        }
    }

    @Test
    void parse_skipsTipsWhenFallingBackToAnUnlabeledOfferAmount() {
        for (String text : java.util.List.of(
                "Tips $18.50\n$72.00", "Propinas\n$18.50\n$72.00", "$72.00 Tips $18.50")) {
            ParsedShiftData result = parser.parse(text, 2026);
            assertThat(result.basePay().value()).as(text).isEqualByComparingTo("72.00");
            assertThat(result.tips().value()).as(text).isEqualByComparingTo("18.50");
        }
    }

    @Test
    void parse_keepsLabeledMoneySourceAndConservativeConfidence() {
        var lines = java.util.List.of(
                new OcrLine("Total $90.50", 99, 0, 0, 100, 20, 0),
                new OcrLine("Base", 50, 0, 20, 100, 20, 1),
                new OcrLine("$72.00", 95, 0, 40, 100, 20, 2),
                new OcrLine("Tips", 95, 0, 60, 100, 20, 3),
                new OcrLine("$18.50", 70, 0, 80, 100, 20, 4));
        ParsedShiftData result = parser.parse(lines, 2026, new ParseContext(LocalDate.of(2026, 9, 7), null));
        assertThat(result.basePay().lineIndex()).isEqualTo(2);
        assertThat(result.basePay().confidence()).isEqualTo(50);
        assertThat(result.basePay().level()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.tips().lineIndex()).isEqualTo(4);
        assertThat(result.tips().confidence()).isEqualTo(70);
        assertThat(result.tips().level()).isEqualTo(ConfidenceLevel.MEDIUM);
    }

    @Test
    void parse_readsLabeledMoneyUsingExistingCurrencyFormats() {
        ParsedShiftData result = parser.parse("Total 90,50 GBP\nBase 72,00 GBP\nTips 18,50 GBP", 2026);
        assertThat(result.basePay().value()).isEqualByComparingTo("72.00");
        assertThat(result.tips().value()).isEqualByComparingTo("18.50");
    }

    @Test
    void parse_extractsScheduleDetailsFields() {
        String rawText = """
                North Haven CT (VEA7/BDL3) - Sub
                Same-Day
                409 Washington Avenue, North Haven, CT, US,
                06473-1307
                Sunday, 9/6 (
                15:15 - 19:15 « (4 hr)
                $124
                Amazon's contribution is $124 for delivering this block.
                """;

        ParsedShiftData result = parser.parse(rawText, 2026);

        assertThat(result.station().value()).isEqualTo("VEA7");
        assertThat(result.date().value()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(result.startTime().value()).isEqualTo(LocalTime.of(15, 15));
        assertThat(result.endTime().value()).isEqualTo(LocalTime.of(19, 15));
        assertThat(result.basePay().value()).isEqualByComparingTo(new BigDecimal("124"));
        assertThat(result.tips().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.warnings()).isEmpty();
    }


    @Test
    void parse_readsSpanishProgramacionCard() {
        // Exactly what Tesseract's English model read from a Flex screenshot taken with the app in Spanish.
        String rawText = """
                = amazonFLex o
                13 MARTES OCTUBRE
                Inicio Pagar
                16:45 $121.50
                Terminar en
                21:15
                Ubicacion y otra informacion
                North Haven CT (VEA7/BDL3) - Sub
                Same-Day
                409 Washington Avenue
                ee
                = EE
                Actualizaciones Programaci6n
                """;

        ParsedShiftData result = parser.parse(rawText, 2026);

        assertThat(result.station().value()).isEqualTo("VEA7");
        assertThat(result.date().value()).isEqualTo(LocalDate.of(2026, 10, 13));
        assertThat(result.startTime().value()).isEqualTo(LocalTime.of(16, 45));
        assertThat(result.endTime().value()).isEqualTo(LocalTime.of(21, 15));
        assertThat(result.basePay().value()).isEqualByComparingTo(new BigDecimal("121.50"));
        assertThat(result.tips().value()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "martes, 13 de octubre|2026-10-13",
            "13 de oct.|2026-10-13",
            "mar., 13 oct.|2026-10-13",
            "5 MIERCOLES MARZO|2026-03-05",
            "1 S4BADO NOVIEMBRE|2026-11-01",
            "13 MARTES OCTUBRE|2026-10-13",
            "3 de septiembre|2026-09-03",
            "7 de Diciembre|2026-12-07"
    })
    void parse_readsSpanishDateForms(String text, LocalDate expected) {
        ParsedShiftData result = parser.parse(text + "\nInicio\n9:00\nTerminar en\n10:00", 2026);

        assertThat(result.date().value()).isEqualTo(expected);
    }

    @Test
    void parse_readsSpanishNumericDateDayFirstAndEnglishMonthFirst() {
        ParsedShiftData spanish = parser.parse("domingo, 6/9\nInicio\n9:00\nTerminar en\n10:00", 2026);
        ParsedShiftData english = parser.parse("Sunday, 6/9\n15:15 - 19:15", 2026);

        assertThat(spanish.date().value()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(english.date().value()).isEqualTo(LocalDate.of(2026, 6, 9));
    }

    @Test
    void parse_readsLabelledTwelveHourTimes() {
        ParsedShiftData evening = parser.parse("Inicio\n4:45 p. m.\nTerminar en\n9:15 p. m.", 2026);
        ParsedShiftData night = parser.parse("Inicio\n12:30 a. m.\nTerminar en\n4:00 a. m.", 2026);

        assertThat(evening.startTime().value()).isEqualTo(LocalTime.of(16, 45));
        assertThat(evening.endTime().value()).isEqualTo(LocalTime.of(21, 15));
        assertThat(night.startTime().value()).isEqualTo(LocalTime.of(0, 30));
        assertThat(night.endTime().value()).isEqualTo(LocalTime.of(4, 0));
    }

    @Test
    void parse_readsEnglishLabelledTimes() {
        ParsedShiftData result = parser.parse("Start\n4:45 PM\nEnd\n9:15 PM", 2026);

        assertThat(result.startTime().value()).isEqualTo(LocalTime.of(16, 45));
        assertThat(result.endTime().value()).isEqualTo(LocalTime.of(21, 15));
    }

    @Test
    void parse_readsPropinas() {
        ParsedShiftData result = parser.parse("Propinas $12.50\n$80", 2026);

        assertThat(result.tips().value()).isEqualByComparingTo(new BigDecimal("12.50"));
    }

    @Test
    void parse_prefersARangeOverLabels() {
        ParsedShiftData result = parser.parse("15:15 - 19:15\nInicio 16:45", 2026);

        assertThat(result.startTime().value()).isEqualTo(LocalTime.of(15, 15));
        assertThat(result.endTime().value()).isEqualTo(LocalTime.of(19, 15));
    }

    @Test
    void parse_doesNotReadMartesAsMarch() {
        ParsedShiftData result = parser.parse("13 MARTES OCTUBRE", 2026);

        assertThat(result.date().value().getMonthValue()).isEqualTo(10);
    }

    @Test
    void parse_extractsNamedDateAndWarnsWhenStationIsNotShown() {
        String rawText = """
                Fri, Sep 4, 17:30 - 20:30
                Payment sent Fri, Sep 4
                Earnings
                Base $72
                """;

        ParsedShiftData result = parser.parse(rawText, 2026);

        assertThat(result.station().value()).isNull();
        assertThat(result.date().value()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(result.startTime().value()).isEqualTo(LocalTime.of(17, 30));
        assertThat(result.endTime().value()).isEqualTo(LocalTime.of(20, 30));
        assertThat(result.basePay().value()).isEqualByComparingTo(new BigDecimal("72"));
        assertThat(result.tips().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.warnings())
                .extracting(ImportWarning::message)
                .containsExactly("Station could not be read from the screenshot.");
    }

    @Test
    void parse_returnsEditableNullFieldsAndWarningsForUnreadableText() {
        ParsedShiftData result = parser.parse("Schedule Details", 2026);

        assertThat(result.station().value()).isNull();
        assertThat(result.date().value()).isNull();
        assertThat(result.startTime().value()).isNull();
        assertThat(result.endTime().value()).isNull();
        assertThat(result.basePay().value()).isNull();
        assertThat(result.tips().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.warnings()).hasSize(5);
    }

    @Test
    void parse_extractsTipsWhenTheyAreShown() {
        String rawText = """
                Windsor (DCY1) - Amazon.com
                Sunday, 9/6
                04:00 - 07:30
                Base $72.00
                Tips $18.50
                """;

        ParsedShiftData result = parser.parse(rawText, 2026);

        assertThat(result.basePay().value()).isEqualByComparingTo(new BigDecimal("72.00"));
        assertThat(result.tips().value()).isEqualByComparingTo(new BigDecimal("18.50"));
    }

    @Test
    void parse_preservesTheSourceLineConfidenceForEachField() {
        var lines = java.util.List.of(
                new OcrLine("Windsor (DCY1)", 58, 0, 0, 100, 20, 0),
                new OcrLine("Sunday, 9/6", 73, 0, 20, 100, 20, 1),
                new OcrLine("04:00 - 07:30", 91, 0, 40, 100, 20, 2),
                new OcrLine("$124.50", 87, 0, 60, 100, 20, 3)
        );

        ParsedShiftData result = parser.parse(
                lines, 2026, new ParseContext(LocalDate.of(2026, 9, 7), null));

        assertThat(result.station().level()).isEqualTo(ConfidenceLevel.LOW);
        assertThat(result.station().lineIndex()).isZero();
        assertThat(result.date().level()).isEqualTo(ConfidenceLevel.MEDIUM);
        assertThat(result.startTime().level()).isEqualTo(ConfidenceLevel.HIGH);
        assertThat(result.basePay().lineIndex()).isEqualTo(3);
    }

    @Test
    void parse_adjustsDecemberToThePreviousYearWhenTodayIsInJanuary() {
        var lines = java.util.List.of(new OcrLine("Monday, 12/28", 95, 0, 0, 100, 20, 0));

        ParsedShiftData result = parser.parse(
                lines, 2027, new ParseContext(LocalDate.of(2027, 1, 5), null));

        assertThat(result.date().value()).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(result.warnings()).extracting(ImportWarning::code).contains("YEAR_ROLLOVER");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // Flex leaves out ":00" on the hour; « and • are how OCR reads the bullet before the duration.
            "16:30 - 21 « (4 hr 30 min)|16:30|21:00",
            "10 - 14:30 • (4 hr 30 min)|10:00|14:30",
            "19 - 22 «(3 hr)|19:00|22:00",
            "04 - 08 • (4 hr)|04:00|08:00",
            "5:30 p.m. - 8 p.m.|17:30|20:00",
            "3:30 AM - 8 AM|03:30|08:00",
            "12 AM - 12:30 AM|00:00|00:30",
            "9 PM - 11 PM|21:00|23:00",
            "6:30 a.m. — 10:30 a.m.|06:30|10:30"
    })
    void parse_readsRangesThatLeaveOutMinutesOnTheHour(String line, LocalTime start, LocalTime end) {
        ParsedShiftData result = parser.parse("Thursday, 10/8\n" + line + "\n$90.00", 2026);

        assertThat(result.startTime().value()).isEqualTo(start);
        assertThat(result.endTime().value()).isEqualTo(end);
    }

    @Test
    void parse_doesNotReadAZipCodeAsHours() {
        ParsedShiftData result = parser.parse("12 Sample Rd, Springfield, CT, US, 06095-1345\nStart\n4:00 AM", 2026);

        assertThat(result.startTime().value()).isEqualTo(LocalTime.of(4, 0));
        assertThat(result.endTime().value()).isNull();
    }

    @Test
    void parse_joinsARangeWrappedOntoTheNextLine() {
        String rawText = """
                Thursday, 9/17
                11:45 AM -                $66
                2:45 PM
                3 hr
                Springfield (DXX9)-Amazon.com
                3:00 PM - 7:00 PM
                """;

        ParsedShiftData result = parser.parse(rawText, 2026);

        assertThat(result.startTime().value()).isEqualTo(LocalTime.of(11, 45));
        assertThat(result.endTime().value()).isEqualTo(LocalTime.of(14, 45));
        assertThat(result.basePay().value()).isEqualByComparingTo(new BigDecimal("66"));
    }

    @Test
    void parse_skipsTheOrdersFilterAndFixesALetterReadForTheLastDigit() {
        ParsedShiftData filter = parser.parse("Orders (Any)\nSpringfield (DXX9) - Amazon.com", 2026);
        ParsedShiftData lookalike = parser.parse("Springfield CT (VXXG/BXX4) - Sub Same-Day", 2026);

        assertThat(filter.station().value()).isEqualTo("DXX9");
        assertThat(lookalike.station().value()).isEqualTo("VXX6");
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            // A date that only works one way round is read that way.
            "Monday 27/02|£47.50|2026-02-27",
            "Friday, 16/12|$176.00|2026-12-16",
            "martes, 06/15|$54|2026-06-15",
            // British and Mexican screenshots write the day first, zero-padded US Spanish ones the month first.
            "Tuesday 06/10|£46.50|2026-10-06",
            "domingo 4/10|$325.00|2026-10-04",
            "martes, 06/10|$54|2026-06-10",
            "Tuesday, 6/10|$54|2026-06-10",
            "MONDAY FEBRUARY 7|$142|2026-02-07",
            "SATURDAY JANUARY 24 (2 OF 3)|$20.50|2026-01-24"
    })
    void parse_readsTheDateInTheOrderTheScreenshotUses(String dateLine, String payLine, LocalDate expected) {
        ParsedShiftData result = parser.parse(dateLine + "\n18:15 - 21:15\n" + payLine, 2026);

        assertThat(result.date().value()).isEqualTo(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
            "£47.50|47.50",
            "MXN 397|397",
            "MXN800.00|800.00",
            "16:15 - 19:15             51,00 GBP|51.00",
            "$1,000|1000"
    })
    void parse_readsPayInOtherCurrencies(String line, BigDecimal expected) {
        ParsedShiftData result = parser.parse("Wednesday, 10/7\n" + line, 2026);

        assertThat(result.basePay().value()).isEqualByComparingTo(expected);
    }

    @Test
    void parse_readsTheSpanishFinalizarLabel() {
        ParsedShiftData result = parser.parse("HOY\nInicio\n19:30\nFinalizar a las\n21:30\n18:36", 2026);

        assertThat(result.startTime().value()).isEqualTo(LocalTime.of(19, 30));
        assertThat(result.endTime().value()).isEqualTo(LocalTime.of(21, 30));
    }
}
