package com.angel.flexbuddy.importcore;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

/** Pure comparison rules. Callers supply only eligible blocks belonging to the current account. */
public final class BlockMatchRules {
    public static final long MINIMUM_OVERLAP_MINUTES = 30;

    private BlockMatchRules() {}

    public enum Kind { EXACT, LIKELY, SCHEDULED_MATCH }

    public record Block(LocalDate date, LocalTime start, LocalTime end, String station, boolean scheduled) {
        public LocalDateTime startDateTime() {
            return LocalDateTime.of(date, start);
        }

        public LocalDateTime endDateTime() {
            long minutes = ChronoUnit.MINUTES.between(start, end);
            return startDateTime().plusMinutes(minutes < 0 ? minutes + 24 * 60 : minutes);
        }
    }

    /** Null means no match. A scheduled existing block can be completed by a completed import. */
    public static Kind match(Block candidate, boolean completedImport, Block existing) {
        if (!stationMatches(candidate.station(), existing.station())
                || overlapMinutes(candidate.startDateTime(), candidate.endDateTime(),
                        existing.startDateTime(), existing.endDateTime()) < MINIMUM_OVERLAP_MINUTES) {
            return null;
        }
        if (existing.scheduled() && completedImport) return Kind.SCHEDULED_MATCH;
        return existing.date().equals(candidate.date()) && existing.start().equals(candidate.start())
                && existing.end().equals(candidate.end()) ? Kind.EXACT : Kind.LIKELY;
    }

    public static long overlapMinutes(LocalDateTime firstStart, LocalDateTime firstEnd,
            LocalDateTime secondStart, LocalDateTime secondEnd) {
        LocalDateTime start = firstStart.isAfter(secondStart) ? firstStart : secondStart;
        LocalDateTime end = firstEnd.isBefore(secondEnd) ? firstEnd : secondEnd;
        return end.isAfter(start) ? Duration.between(start, end).toMinutes() : 0;
    }

    public static boolean stationMatches(String candidate, String existing) {
        if (candidate == null || candidate.isBlank()) return true;
        if (existing == null) return false;
        String imported = candidate.trim().toLowerCase(Locale.ROOT);
        String planned = existing.trim().toLowerCase(Locale.ROOT);
        return imported.equals(planned) || imported.contains(planned) || planned.contains(imported);
    }
}
