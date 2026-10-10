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
}
