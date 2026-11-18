package com.angel.flexbuddy.importcore.figures;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlannedWorkTest {
    private static final LocalDate DATE = LocalDate.of(2026, 12, 31);
    private static final LocalDateTime NOW = DATE.atTime(12, 0);

    @Test void plannedAndConfirmationHaveSeparateWindowsAndExactEndRules() {
        var rows = List.of(block(0, "11:00", "13:00", "20"), block(0, "23:00", "01:00", "80"),
                block(6, "08:00", "09:00", "30"), block(7, "08:00", "09:00", "999"),
                block(0, "10:00", "12:00", "10"), block(0, "12:00", "12:00", "10"),
                block(-1, "23:00", "01:00", "10"), block(-100, "08:00", "10:00", "10"));
        var result = PlannedWork.of(rows, rows, NOW);
        assertEquals(new PlannedWork(3, 300, new BigDecimal("130.00"), 4), result);
    }

    @Test void priorDayOvernightStillInProgressIsNeitherUpcomingNorOverdue() {
        var rows = List.of(block(-1, "23:00", "01:00", "100"));
        assertEquals(new PlannedWork(0, 0, new BigDecimal("0.00"), 0), PlannedWork.of(rows, rows, DATE.atTime(0, 30)));
        assertEquals(1, PlannedWork.of(rows, rows, DATE.atTime(1, 0)).needsConfirmation());
    }

    @Test void futureStartsAndEqualTimeBlocksPreserveZeroDurationAndIgnoreTipsAndOtherStatuses() {
        Block upcoming = block(0, "17:00", "18:00", "20.005");
        Block zero = block(0, "16:00", "16:00", "10.005");
        Block completed = new Block(BlockStatus.COMPLETED, "ZZT9", DATE, LocalTime.of(17, 0), LocalTime.of(18, 0),
                new BigDecimal("999"), BigDecimal.TEN, BigDecimal.ZERO);
        var rows = new ArrayList<>(List.of(upcoming, zero, completed));
        var copy = List.copyOf(rows);
        var result = PlannedWork.of(rows, rows, NOW);
        assertEquals(new PlannedWork(2, 60, new BigDecimal("30.01"), 0), result); // Round sum, not each base pay.
        assertEquals(copy, rows);
        rows.clear();
        assertEquals(new BigDecimal("30.01"), result.expectedPay());
        var confirmed = NetEarnings.calculate(List.of(completed), List.of(), VehicleCostMethod.ACTUAL_EXPENSES, null);
        var withScheduled = NetEarnings.calculate(List.of(completed, upcoming, zero), List.of(), VehicleCostMethod.ACTUAL_EXPENSES, null);
        assertEquals(confirmed.grossEarnings(), withScheduled.grossEarnings());
        assertEquals(confirmed.minutesWorked(), withScheduled.minutesWorked());
        assertEquals(confirmed.netEarnings(), withScheduled.netEarnings());
    }

    @Test void emptyIsZeroAndSeparateQueryCandidatesDoNotDoubleCountToday() {
        assertEquals(new PlannedWork(0, 0, new BigDecimal("0.00"), 0), PlannedWork.of(List.of(), List.of(), NOW));
        var today = block(0, "11:00", "13:00", "20");
        assertEquals(new PlannedWork(1, 120, new BigDecimal("20.00"), 0), PlannedWork.of(List.of(today), List.of(today), NOW));
    }

    private static Block block(int day, String start, String end, String base) {
        return new Block(BlockStatus.SCHEDULED, "ZZT9", DATE.plusDays(day), LocalTime.parse(start), LocalTime.parse(end),
                new BigDecimal(base), new BigDecimal("999"), BigDecimal.ZERO);
    }
}
