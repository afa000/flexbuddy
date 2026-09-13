package com.angel.flexbuddy.service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/** Writes RFC 5545 iCalendar text: CRLF line endings, escaped text values, and lines folded at 75 octets. */
@Component
public class IcsWriter {

    private static final DateTimeFormatter LOCAL = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss");
    private static final DateTimeFormatter UTC = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
            .withZone(ZoneOffset.UTC);
    private static final int MAX_LINE_OCTETS = 75;

    public record Event(String uid, LocalDateTime start, LocalDateTime end, String summary, String description,
            String url, boolean cancelled, Integer alarmMinutesBefore) {}

    public String write(String calendarName, ZoneId zone, Instant stamp, List<Event> events, Duration refreshInterval) {
        List<String> lines = new ArrayList<>();
        lines.add("BEGIN:VCALENDAR");
        lines.add("VERSION:2.0");
        lines.add("PRODID:-//FlexBuddy//Schedule//EN");
        lines.add("CALSCALE:GREGORIAN");
        lines.add("METHOD:PUBLISH");
        if (calendarName != null) {
            lines.add("X-WR-CALNAME:" + escape(calendarName));
            lines.add("X-WR-TIMEZONE:" + zone.getId());
        }
        if (refreshInterval != null) {
            lines.add("REFRESH-INTERVAL;VALUE=DURATION:" + refreshInterval);
            lines.add("X-PUBLISHED-TTL:" + refreshInterval);
        }
        for (Event event : events) {
            lines.add("BEGIN:VEVENT");
            lines.add("UID:" + event.uid());
            lines.add("DTSTAMP:" + UTC.format(stamp));
            lines.add("DTSTART;TZID=" + zone.getId() + ":" + LOCAL.format(event.start()));
            lines.add("DTEND;TZID=" + zone.getId() + ":" + LOCAL.format(event.end()));
            lines.add("SUMMARY:" + escape(event.summary()));
            if (event.description() != null) lines.add("DESCRIPTION:" + escape(event.description()));
            if (event.url() != null) lines.add("URL:" + event.url());
            lines.add("STATUS:" + (event.cancelled() ? "CANCELLED" : "CONFIRMED"));
            if (event.alarmMinutesBefore() != null && !event.cancelled()) {
                lines.add("BEGIN:VALARM");
                lines.add("ACTION:DISPLAY");
                lines.add("DESCRIPTION:" + escape(event.summary()));
                lines.add("TRIGGER:-PT" + event.alarmMinutesBefore() + "M");
                lines.add("END:VALARM");
            }
            lines.add("END:VEVENT");
        }
        lines.add("END:VCALENDAR");

        StringBuilder output = new StringBuilder();
        lines.forEach(line -> output.append(fold(line)).append("\r\n"));
        return output.toString();
    }

    static String escape(String value) {
        return value.replace("\\", "\\\\")
                .replace(";", "\\;")
                .replace(",", "\\,")
                .replace("\r\n", "\\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }

    static String fold(String line) {
        if (line.getBytes(StandardCharsets.UTF_8).length <= MAX_LINE_OCTETS) return line;
        StringBuilder folded = new StringBuilder();
        int octets = 0;
        for (int index = 0; index < line.length(); ) {
            int codePoint = line.codePointAt(index);
            int width = new String(Character.toChars(codePoint)).getBytes(StandardCharsets.UTF_8).length;
            if (octets + width > MAX_LINE_OCTETS) {
                folded.append("\r\n ");
                octets = 1;
            }
            folded.appendCodePoint(codePoint);
            octets += width;
            index += Character.charCount(codePoint);
        }
        return folded.toString();
    }
}
