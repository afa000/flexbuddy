package com.angel.flexbuddy.importcore.figures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * The history behind a goal's averages and scheduled-pay estimate. Consumers supply the account's countable
 * report earnings for {@link #historyStart} through today, including cancelled earnings and costs, and completed
 * blocks from that same range. The completed sample alone supplies the denominator and minutes; it must not
 * replace the report earnings numerator. No input list is retained or modified.
 */
public record GoalPreparation(boolean netBasis, BigDecimal averageBlockPay, Integer averageBlockMinutes,
        BigDecimal keptShare) {
    public static final int HISTORY_DAYS = 90;

    /** Inclusive first date of the account's 90-day history. The caller supplies its own local today. */
    public static LocalDate historyStart(LocalDate today) {
        return today.minusDays(HISTORY_DAYS - 1L);
    }

    public static GoalPreparation fromHistory(NetEarnings history, List<Block> completed, boolean netBasis) {
        int count = 0;
        int minutes = 0;
        for (Block block : completed) {
            if (block.effectiveStatus() == BlockStatus.COMPLETED) {
                count++;
                minutes += block.workedMinutes();
            }
        }
        BigDecimal average = count == 0 ? null : (netBasis ? history.netEarnings() : history.grossEarnings())
                .divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
        Integer averageMinutes = count == 0 ? null : Math.round((float) minutes / count);
        BigDecimal share = !netBasis || history.grossEarnings().signum() == 0 ? BigDecimal.ONE
                : history.netEarnings().divide(history.grossEarnings(), 4, RoundingMode.HALF_UP);
        return new GoalPreparation(netBasis, average, averageMinutes, share);
    }

    public BigDecimal earned(NetEarnings period) {
        return netBasis ? period.netEarnings() : period.grossEarnings();
    }

    /**
     * Consumers select live scheduled blocks in the goal period, including overdue ones. Tips do not count.
     * Keep this value unrounded until GoalProgress.of: rounding before remaining/pace/blocks-to-go changes results.
     * Historical shares may be negative or greater than one; the website does not clamp them.
     */
    public BigDecimal plannedPay(List<Block> scheduled) {
        BigDecimal gross = BigDecimal.ZERO;
        for (Block block : scheduled) {
            if (block.effectiveStatus() == BlockStatus.SCHEDULED) gross = gross.add(block.basePay());
        }
        return gross.multiply(keptShare);
    }
}
