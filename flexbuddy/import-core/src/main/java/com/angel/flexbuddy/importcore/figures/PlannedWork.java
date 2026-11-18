package com.angel.flexbuddy.importcore.figures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Upcoming gross work and overdue scheduled confirmations, separate from confirmed earnings and goal estimates. */
public record PlannedWork(int plannedShifts, int plannedMinutes, BigDecimal expectedPay, int needsConfirmation) {
    /**
     * Two candidate lists let a database consumer keep bounded queries: upcoming covers today..today+6 and
     * unconfirmed covers all dates through today. An offline consumer may pass its full scheduled cache to both.
     * They may overlap: each list feeds different fields, so today's blocks are never double-counted.
     * Other statuses are ignored. Inputs are live rows of one account with required date/start/end/base-pay values.
     * No clock, zone, collection mutation or retained list is involved.
     */
    public static PlannedWork of(List<Block> upcoming, List<Block> unconfirmed, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDate through = today.plusDays(6);
        int count = 0;
        int minutes = 0;
        BigDecimal gross = BigDecimal.ZERO;
        for (Block block : upcoming) {
            if (block.effectiveStatus() == BlockStatus.SCHEDULED && !block.date().isBefore(today)
                    && !block.date().isAfter(through) && end(block).isAfter(now)) {
                count++;
                minutes += block.timeWorked();
                gross = gross.add(block.basePay());
            }
        }
        int overdue = 0;
        for (Block block : unconfirmed) {
            if (block.effectiveStatus() == BlockStatus.SCHEDULED && !block.date().isAfter(today)
                    && !end(block).isAfter(now)) overdue++;
        }
        return new PlannedWork(count, minutes, gross.setScale(2, RoundingMode.HALF_UP), overdue);
    }

    /** Match the server's start plus shared duration, including overnight and equal-time zero-length blocks. */
    private static LocalDateTime end(Block block) {
        return block.date().atTime(block.start()).plusMinutes(block.timeWorked());
    }
}
