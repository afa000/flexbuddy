package com.angel.flexbuddy.importcore;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class BlockMatchRulesTest {
    private final LocalDate day = LocalDate.of(2026, 9, 13);

    private BlockMatchRules.Block block(LocalDate date, String start, String end, String station, boolean scheduled) {
        return new BlockMatchRules.Block(date, LocalTime.parse(start), LocalTime.parse(end), station, scheduled);
    }

    @Test
    void requiresThirtyWholeMinutesAndMatchingStations() {
        var existing = block(day, "15:15", "19:15", "VEA7", true);
        assertEquals(BlockMatchRules.Kind.SCHEDULED_MATCH,
                BlockMatchRules.match(block(day, "18:45", "21:00", "Windsor (vea7)", false), true, existing));
        assertNull(BlockMatchRules.match(block(day, "18:46", "21:00", "VEA7", false), true, existing));
        assertNull(BlockMatchRules.match(block(day, "15:15", "19:15", "BDL4", false), true, existing));
        assertEquals(BlockMatchRules.Kind.SCHEDULED_MATCH,
                BlockMatchRules.match(block(day, "15:15", "19:15", null, false), true, existing));
        assertFalse(BlockMatchRules.stationMatches("VEA7", null));
        assertTrue(BlockMatchRules.stationMatches(" ", null));
    }

    @Test
    void distinguishesCompletionFromExactAndLikelyDuplicates() {
        var candidate = block(day, "15:15", "19:15", "VEA7", false);
        var scheduled = block(day, "15:15", "19:15", "VEA7", true);
        var completed = block(day, "15:15", "19:15", "VEA7", false);
        assertEquals(BlockMatchRules.Kind.SCHEDULED_MATCH, BlockMatchRules.match(candidate, true, scheduled));
        assertEquals(BlockMatchRules.Kind.EXACT, BlockMatchRules.match(candidate, false, scheduled));
        assertEquals(BlockMatchRules.Kind.EXACT, BlockMatchRules.match(candidate, true, completed));
        assertEquals(BlockMatchRules.Kind.LIKELY, BlockMatchRules.match(candidate, true,
                block(day, "15:45", "19:45", "VEA7", false)));
    }

    @Test
    void handlesOvernightImportsAndPreviousDayBlocks() {
        var overnight = block(day, "22:00", "02:00", "VEA7", false);
        assertEquals(BlockMatchRules.Kind.LIKELY, BlockMatchRules.match(
                block(day.plusDays(1), "00:30", "02:00", "VEA7", false), true, overnight));
        assertEquals(BlockMatchRules.Kind.LIKELY, BlockMatchRules.match(
                block(day, "23:30", "01:30", "VEA7", false), true, overnight));
    }

    @Test
    void preservesZeroDurationForEqualTimesAndExcludesTouchingIntervals() {
        var existing = block(day, "15:15", "19:15", "VEA7", true);
        var zero = block(day, "16:00", "16:00", "VEA7", false);
        assertEquals(zero.startDateTime(), zero.endDateTime());
        assertNull(BlockMatchRules.match(zero, true, existing));
        assertNull(BlockMatchRules.match(block(day, "19:15", "20:15", "VEA7", false), true, existing));
    }
}
