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

        assertThat(result.station()).isEqualTo("VEA7");
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(result.startTime()).isEqualTo(LocalTime.of(15, 15));
        assertThat(result.endTime()).isEqualTo(LocalTime.of(19, 15));
        assertThat(result.basePay()).isEqualByComparingTo(new BigDecimal("124"));
        assertThat(result.tips()).isEqualByComparingTo(BigDecimal.ZERO);
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

        assertThat(result.station()).isNull();
        assertThat(result.date()).isEqualTo(LocalDate.of(2026, 9, 4));
        assertThat(result.startTime()).isEqualTo(LocalTime.of(17, 30));
        assertThat(result.endTime()).isEqualTo(LocalTime.of(20, 30));
        assertThat(result.basePay()).isEqualByComparingTo(new BigDecimal("72"));
        assertThat(result.tips()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.warnings())
                .containsExactly("Station could not be read from the screenshot.");
    }

    @Test
    void parse_returnsEditableNullFieldsAndWarningsForUnreadableText() {
        ParsedShiftData result = parser.parse("Schedule Details", 2026);

        assertThat(result.station()).isNull();
        assertThat(result.date()).isNull();
        assertThat(result.startTime()).isNull();
        assertThat(result.endTime()).isNull();
        assertThat(result.basePay()).isNull();
        assertThat(result.tips()).isEqualByComparingTo(BigDecimal.ZERO);
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

        assertThat(result.basePay()).isEqualByComparingTo(new BigDecimal("72.00"));
        assertThat(result.tips()).isEqualByComparingTo(new BigDecimal("18.50"));
    }
}
