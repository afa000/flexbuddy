package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

class IcsWriterTest {

    private final IcsWriter writer = new IcsWriter();
    private final ZoneId chicago = ZoneId.of("America/Chicago");
    private final Instant stamp = Instant.parse("2026-09-12T12:00:00Z");

    @Test
    void writesAScheduledBlockWithItsTimeZoneAlarmAndCrlfLines() {
        String ics = writer.write("FlexBuddy", chicago, stamp, List.of(event(false, 60, "Offered, not yet worked")),
                Duration.ofHours(1));

        assertThat(ics).startsWith("BEGIN:VCALENDAR\r\n").endsWith("END:VCALENDAR\r\n");
        assertThat(ics.replace("\r\n", "")).doesNotContain("\n", "\r");
        assertThat(ics).contains(
                "X-WR-CALNAME:FlexBuddy\r\n",
                "REFRESH-INTERVAL;VALUE=DURATION:PT1H\r\n",
                "UID:shift-7@flexbuddy\r\n",
                "DTSTAMP:20260912T120000Z\r\n",
                "DTSTART;TZID=America/Chicago:20260913T151500\r\n",
                "DTEND;TZID=America/Chicago:20260913T191500\r\n",
                "SUMMARY:Flex block · VEA7 · $84.00\r\n",
                "DESCRIPTION:Offered\\, not yet worked\r\n",
                "STATUS:CONFIRMED\r\n",
                "BEGIN:VALARM\r\nACTION:DISPLAY\r\n",
                "TRIGGER:-PT60M\r\n");
    }

    @Test
    void cancelledBlocksCarryACancelledStatusAndNoAlarm() {
        String ics = writer.write(null, chicago, stamp, List.of(event(true, 60, null)), null);

        assertThat(ics).contains("STATUS:CANCELLED\r\n").doesNotContain("VALARM", "X-WR-CALNAME", "REFRESH-INTERVAL");
    }

    @Test
    void escapesTextValues() {
        assertThat(IcsWriter.escape("a\\b;c,d\ne\r\nf")).isEqualTo("a\\\\b\\;c\\,d\\ne\\nf");
    }

    @Test
    void foldsLongLinesAtSeventyFiveOctetsWithoutSplittingCharacters() {
        String summary = "Flex block · " + "Überstunden ".repeat(12);
        String ics = writer.write(null, chicago, stamp, List.of(new IcsWriter.Event("shift-1@flexbuddy",
                LocalDateTime.of(2026, 9, 13, 8, 0), LocalDateTime.of(2026, 9, 13, 12, 0), summary, null, null,
                false, null)), null);

        List<String> lines = Arrays.asList(ics.split("\r\n"));
        assertThat(lines).allSatisfy(line -> assertThat(line.getBytes(StandardCharsets.UTF_8).length).isLessThanOrEqualTo(75));
        assertThat(lines).anySatisfy(line -> assertThat(line).startsWith(" "));
        assertThat(ics.replace("\r\n ", "")).contains("SUMMARY:" + summary.trim());
    }

    private IcsWriter.Event event(boolean cancelled, Integer alarm, String description) {
        return new IcsWriter.Event("shift-7@flexbuddy", LocalDateTime.of(2026, 9, 13, 15, 15),
                LocalDateTime.of(2026, 9, 13, 19, 15), "Flex block · VEA7 · $84.00", description,
                "https://flexbuddy.example/?screen=schedule", cancelled, alarm);
    }
}
