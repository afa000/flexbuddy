package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.springframework.stereotype.Component;

import com.angel.flexbuddy.dto.GoalProgress;

/**
 * Works out how far along a goal is, with the shared library's rule
 * ({@link com.angel.flexbuddy.importcore.figures.GoalProgress}). A goal is on track when what is earned plus what is
 * scheduled reaches it, or when the pace so far, earned per elapsed day across the whole period, would.
 */
@Component
public class GoalProgressCalculator {

    public GoalProgress progress(BigDecimal goal, BigDecimal earned, BigDecimal planned, BigDecimal averageBlockPay,
            Integer averageBlockMinutes, LocalDate start, LocalDate end, LocalDate today) {
        var progress = com.angel.flexbuddy.importcore.figures.GoalProgress.of(goal, earned, planned, averageBlockPay,
                averageBlockMinutes, start, end, today);
        if (progress == null) return null;
        return new GoalProgress(progress.goal(), progress.earned(), progress.planned(), progress.remaining(),
                progress.percent(), progress.onTrack(), progress.blocksToGo(), progress.averageBlockPay(),
                progress.averageBlockMinutes(), progress.periodStart(), progress.periodEnd());
    }
}
