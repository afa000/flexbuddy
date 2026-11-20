package com.angel.flexbuddy.importcore.figures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;

/**
 * How a block went, from its actual times and route counts: time on the clock, how early it finished, minutes per
 * stop and the returns rate. Only a worked (completed) block has clocked time, an early finish or minutes per stop.
 */
public final class RouteRules {

    private RouteRules() {
    }

    /** Minutes between the actual start and finish, across midnight when the finish is earlier; null unless both. */
    public static Integer actualMinutes(LocalTime actualStart, LocalTime actualEnd) {
        if (actualStart == null || actualEnd == null) return null;
        return EarningsRules.timeWorked(actualStart, actualEnd);
    }

    /** Time on the clock for a worked block: the actual duration when recorded, otherwise the scheduled one. */
    public static int clockedMinutes(BlockStatus status, LocalTime start, LocalTime end, Integer actualMinutes) {
        if (!EarningsRules.countsTowardHours(status)) return 0;
        return actualMinutes == null ? EarningsRules.timeWorked(start, end) : actualMinutes;
    }

    /** How much sooner than scheduled a worked block finished; negative when it ran over, null when untimed. */
    public static Integer finishedEarlyMinutes(BlockStatus status, LocalTime start, LocalTime end,
            Integer actualMinutes) {
        return actualMinutes == null || !EarningsRules.countsTowardHours(status) ? null
                : EarningsRules.timeWorked(start, end) - actualMinutes;
    }

    /** Minutes on the clock per stop for a worked block with stops recorded, to one place; null otherwise. */
    public static BigDecimal minutesPerStop(BlockStatus status, LocalTime start, LocalTime end, Integer actualMinutes,
            Integer stops) {
        if (!EarningsRules.countsTowardHours(status) || stops == null || stops == 0) return null;
        return BigDecimal.valueOf(clockedMinutes(status, start, end, actualMinutes))
                .divide(BigDecimal.valueOf(stops), 1, RoundingMode.HALF_UP);
    }

    /**
     * What returns are a share of: packages carried, or stops when packages were not counted. Null without returns,
     * and null when that count is missing or zero (zero packages does not fall back to stops).
     */
    public static Integer returnsBase(Integer stops, Integer packages, Integer returns) {
        if (returns == null) return null;
        Integer base = packages != null ? packages : stops;
        return base == null || base == 0 ? null : base;
    }

    /** Returns as a percentage of {@link #returnsBase}, to one place; null when there is no base. */
    public static BigDecimal returnsRate(Integer stops, Integer packages, Integer returns) {
        Integer base = returnsBase(stops, packages, returns);
        return base == null ? null
                : BigDecimal.valueOf(returns * 100L).divide(BigDecimal.valueOf(base), 1, RoundingMode.HALF_UP);
    }
}
