package com.angel.flexbuddy.importcore.figures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.Test;

class EarningsRulesTest {
    private static final LocalDate DAY = LocalDate.of(2026, 9, 14);

    @Test
    void aMissingStatusCountsAsCompleted() {
        Block block = block(null, "10:00", "13:30", "63.00", "12.40", "41.3");

        assertEquals(BlockStatus.COMPLETED, block.effectiveStatus());
        assertEquals(new BigDecimal("75.40"), block.earnedPay());
        assertEquals(210, block.workedMinutes());
        assertEquals(new BigDecimal("41.3"), block.countedMiles());
    }

    @Test
    void aScheduledBlockCountsForNothing() {
        Block block = block(BlockStatus.SCHEDULED, "18:00", "21:00", "54.00", "5.00", "12.0");

        assertFalse(EarningsRules.countsTowardEarnings(BlockStatus.SCHEDULED));
        assertFalse(block.countsTowardHours());
        assertFalse(EarningsRules.countsTowardMiles(BlockStatus.SCHEDULED));
        assertEquals(BigDecimal.ZERO, block.earnedBasePay());
        assertEquals(BigDecimal.ZERO, block.earnedTips());
        assertEquals(0, block.workedMinutes());
        assertNull(block.countedMiles());
        assertEquals(180, block.timeWorked(), "the scheduled length is still known");
    }

    @Test
    void aCancelledBlockKeepsItsBasePayAndMilesButNoTipsOrHours() {
        Block block = block(BlockStatus.CANCELLED, "10:00", "13:30", "18.00", "4.00", "20.0");

        assertEquals(new BigDecimal("18.00"), block.earnedPay());
        assertEquals(0, block.workedMinutes());
        assertEquals(new BigDecimal("20.0"), block.countedMiles());
    }

    @Test
    void aForfeitedBlockCountsOnlyItsMiles() {
        Block block = block(BlockStatus.FORFEITED, "10:00", "13:30", "10.00", "2.00", "15.0");

        assertFalse(EarningsRules.countsTowardEarnings(BlockStatus.FORFEITED));
        assertEquals(BigDecimal.ZERO, block.earnedPay());
        assertEquals(0, block.workedMinutes());
        assertEquals(new BigDecimal("15.0"), block.countedMiles());
    }

    @Test
    void missingPayCountsAsZero() {
        Block block = new Block(BlockStatus.COMPLETED, "DSE4", DAY, LocalTime.NOON, LocalTime.of(14, 0), null, null, null);

        assertEquals(BigDecimal.ZERO, block.earnedPay());
        assertNull(block.countedMiles());
    }

    @Test
    void timeRunsAcrossMidnightAndNeedsBothEnds() {
        assertEquals(210, EarningsRules.timeWorked(LocalTime.of(22, 0), LocalTime.of(1, 30)));
        assertEquals(0, EarningsRules.timeWorked(LocalTime.of(9, 0), LocalTime.of(9, 0)));
        assertEquals(0, EarningsRules.timeWorked(null, LocalTime.of(9, 0)));
        assertEquals(0, EarningsRules.timeWorked(LocalTime.of(9, 0), null));
    }

    @Test
    void anExpenseWaitsOnlyForALiveScheduledBlock() {
        assertTrue(EarningsRules.expenseWaits(BlockStatus.SCHEDULED, false));
        assertFalse(EarningsRules.expenseWaits(BlockStatus.SCHEDULED, true), "a deleted block's expense counts by date");
        assertFalse(EarningsRules.expenseWaits(BlockStatus.COMPLETED, false));
        assertFalse(EarningsRules.expenseWaits(BlockStatus.CANCELLED, false));
        assertFalse(EarningsRules.expenseWaits(null, false));
    }

    private static Block block(BlockStatus status, String start, String end, String base, String tips, String miles) {
        return new Block(status, "DSE4", DAY, LocalTime.parse(start), LocalTime.parse(end), new BigDecimal(base),
                new BigDecimal(tips), miles == null ? null : new BigDecimal(miles));
    }
}
