package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

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
