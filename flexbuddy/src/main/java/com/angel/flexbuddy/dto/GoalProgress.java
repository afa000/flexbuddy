package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Progress toward one goal. Planned is the pay of blocks still scheduled in the period; remaining never goes
 * below zero. The block figures describe a typical completed block over the last 90 days and are null without one.
 */
public record GoalProgress(
        BigDecimal goal,
        BigDecimal earned,
        BigDecimal planned,
        BigDecimal remaining,
        BigDecimal percent,
        boolean onTrack,
        int blocksToGo,
        BigDecimal averageBlockPay,
        Integer averageBlockMinutes,
        LocalDate periodStart,
        LocalDate periodEnd
) {
}
