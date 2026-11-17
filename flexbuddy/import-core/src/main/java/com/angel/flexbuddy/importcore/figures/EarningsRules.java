package com.angel.flexbuddy.importcore.figures;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

/**
 * What a block adds to the figures, by status. A missing status means completed. Base pay counts for completed and
 * cancelled blocks, tips and hours only for completed ones, and miles for every block except one still scheduled. A
 * scheduled block counts toward nothing until it is confirmed.
 */
public final class EarningsRules {
    private static final int DAY_MINUTES = 24 * 60;

    private EarningsRules() {
    }

    public static BlockStatus effective(BlockStatus status) {
        return status == null ? BlockStatus.COMPLETED : status;
    }

    public static boolean countsTowardEarnings(BlockStatus status) {
        BlockStatus effective = effective(status);
        return effective == BlockStatus.COMPLETED || effective == BlockStatus.CANCELLED;
    }

    /** Only a worked block adds hours. */
    public static boolean countsTowardHours(BlockStatus status) {
        return effective(status) == BlockStatus.COMPLETED;
    }

    /** The drive to the station happened for every block except one that is still scheduled. */
    public static boolean countsTowardMiles(BlockStatus status) {
        return effective(status) != BlockStatus.SCHEDULED;
    }

    /** Minutes from start to end, across midnight when the end is earlier; 0 unless both are known. */
    public static int timeWorked(LocalTime start, LocalTime end) {
        if (start == null || end == null) return 0;
        int minutes = (int) ChronoUnit.MINUTES.between(start, end);
        return minutes < 0 ? minutes + DAY_MINUTES : minutes;
    }

    public static BigDecimal earnedBasePay(BlockStatus status, BigDecimal basePay) {
        return countsTowardEarnings(status) && basePay != null ? basePay : BigDecimal.ZERO;
    }

    public static BigDecimal earnedTips(BlockStatus status, BigDecimal tips) {
        return effective(status) == BlockStatus.COMPLETED && tips != null ? tips : BigDecimal.ZERO;
    }

    public static int workedMinutes(BlockStatus status, LocalTime start, LocalTime end) {
        return countsTowardHours(status) ? timeWorked(start, end) : 0;
    }

    public static BigDecimal countedMiles(BlockStatus status, BigDecimal miles) {
        return countsTowardMiles(status) ? miles : null;
    }

    /**
     * Whether an expense linked to a block waits for it: it does while the block is still scheduled, and one on a
     * deleted block counts by its own date.
     */
    public static boolean expenseWaits(BlockStatus linkedStatus, boolean linkedDeleted) {
        return !linkedDeleted && effective(linkedStatus) == BlockStatus.SCHEDULED;
    }
}
