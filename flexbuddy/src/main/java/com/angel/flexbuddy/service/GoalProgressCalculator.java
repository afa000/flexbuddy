package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import com.angel.flexbuddy.dto.GoalProgress;

/**
 * Works out how far along a goal is. A goal is on track when what is earned plus what is scheduled reaches it, or
 * when the pace so far, earned per elapsed day across the whole period, would.
 */
@Component
public class GoalProgressCalculator {

    public GoalProgress progress(BigDecimal goal, BigDecimal earned, BigDecimal planned, BigDecimal averageBlockPay,
            Integer averageBlockMinutes, LocalDate start, LocalDate end, LocalDate today) {
        if (goal == null) return null;
        BigDecimal remaining = goal.subtract(earned).subtract(planned).max(BigDecimal.ZERO);
        BigDecimal percent = earned.multiply(BigDecimal.valueOf(100)).divide(goal, 1, RoundingMode.HALF_UP);
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        long elapsed = Math.min(days, Math.max(1, ChronoUnit.DAYS.between(start, today) + 1));
        BigDecimal pace = earned.multiply(BigDecimal.valueOf(days)).divide(BigDecimal.valueOf(elapsed), 2, RoundingMode.HALF_UP);
        boolean onTrack = earned.add(planned).compareTo(goal) >= 0 || pace.compareTo(goal) >= 0;
        int blocksToGo = remaining.signum() == 0 || averageBlockPay == null || averageBlockPay.signum() <= 0 ? 0
                : remaining.divide(averageBlockPay, 0, RoundingMode.CEILING).intValueExact();
        return new GoalProgress(money(goal), money(earned), money(planned), money(remaining), percent, onTrack,
                blocksToGo, averageBlockPay == null ? null : money(averageBlockPay), averageBlockMinutes, start, end);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
