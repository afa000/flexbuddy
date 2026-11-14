package com.angel.flexbuddy.api;

import static com.angel.flexbuddy.api.ApiValues.code;
import static com.angel.flexbuddy.api.ApiValues.date;
import static com.angel.flexbuddy.api.ApiValues.decimal;
import static com.angel.flexbuddy.api.ApiValues.money;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.angel.flexbuddy.dto.GoalProgress;
import com.angel.flexbuddy.dto.GoalsResponse;
import com.angel.flexbuddy.dto.ShiftStatisticsResponse;

/** The figures the website's Home shows, computed by the same services, in the API's conventions. */
public record ApiHome(String today, Statistics week, Statistics lastSevenDays, Goals goals, List<Long> missingMiles) {

    public record Statistics(
            String from,
            String to,
            int totalShifts,
            String totalBasePay,
            String totalTips,
            String totalEarnings,
            String averagePayPerShift,
            int totalTimeWorked,
            String averageHourlyEarnings,
            String averageHourlyBasePay,
            String averageHourlyTips,
            String averageBasePerShift,
            String averageTipsPerShift,
            String tipsShareOfEarnings,
            int averageShiftMinutes,
            String totalMiles,
            String mileageCost,
            String totalExpenses,
            String totalDeductions,
            String netEarnings,
            String netHourlyRate,
            String earningsPerMile,
            String netMargin,
            Map<String, String> expensesByCategory,
            String vehicleCost,
            String outOfPocket,
            String netPerShift,
            String vehicleCostMethod,
            String mileageRate,
            int rollingSevenDayMinutes,
            int scheduledShifts,
            int scheduledMinutes,
            String expectedPay,
            int needsConfirmation,
            int cancelledShifts,
            int forfeitedShifts,
            int forfeitedThisMonth,
            int timedShifts,
            int clockedMinutes,
            String clockedHourlyRate,
            Integer averageFinishedEarlyMinutes,
            int lateForfeitedShifts,
            int lateForfeitedThisMonth,
            int blocksWithoutMiles
    ) {
        static Statistics of(LocalDate from, LocalDate to, ShiftStatisticsResponse s) {
            Map<String, String> byCategory = new TreeMap<>();
            if (s.getExpensesByCategory() != null) {
                s.getExpensesByCategory().forEach((category, amount) -> byCategory.put(code(category), money(amount)));
            }
            return new Statistics(date(from), date(to), s.getTotalShifts(), money(s.getTotalBasePay()),
                    money(s.getTotalTips()), money(s.getTotalEarnings()), money(s.getAveragePayPerShift()),
                    s.getTotalTimeWorked(), money(s.getAverageHourlyEarnings()), money(s.getAverageHourlyBasePay()),
                    money(s.getAverageHourlyTips()), money(s.getAverageBasePerShift()),
                    money(s.getAverageTipsPerShift()), decimal(s.getTipsShareOfEarnings()),
                    s.getAverageShiftMinutes(), decimal(s.getTotalMiles()), money(s.getMileageCost()),
                    money(s.getTotalExpenses()), money(s.getTotalDeductions()), money(s.getNetEarnings()),
                    money(s.getNetHourlyRate()), money(s.getEarningsPerMile()), decimal(s.getNetMargin()),
                    byCategory, money(s.getVehicleCost()), money(s.getOutOfPocket()), money(s.getNetPerShift()),
                    code(s.getVehicleCostMethod()), decimal(s.getMileageRate()), s.getRollingSevenDayMinutes(),
                    s.getScheduledShifts(), s.getScheduledMinutes(), money(s.getExpectedPay()),
                    s.getNeedsConfirmation(), s.getCancelledShifts(), s.getForfeitedShifts(),
                    s.getForfeitedThisMonth(), s.getTimedShifts(), s.getClockedMinutes(),
                    money(s.getClockedHourlyRate()), s.getAverageFinishedEarlyMinutes(), s.getLateForfeitedShifts(),
                    s.getLateForfeitedThisMonth(), s.getBlocksWithoutMiles());
        }
    }

    /** A goal that isn't set is null, as on the website. */
    public record Goals(String basis, Progress week, Progress month) {
        static Goals of(GoalsResponse goals) {
            return new Goals(code(goals.basis()), Progress.of(goals.week()), Progress.of(goals.month()));
        }
    }

    public record Progress(
            String goal,
            String earned,
            String planned,
            String remaining,
            String percent,
            boolean onTrack,
            int blocksToGo,
            String averageBlockPay,
            Integer averageBlockMinutes,
            String periodStart,
            String periodEnd
    ) {
        static Progress of(GoalProgress progress) {
            if (progress == null) return null;
            return new Progress(money(progress.goal()), money(progress.earned()), money(progress.planned()),
                    money(progress.remaining()), decimal(progress.percent()), progress.onTrack(),
                    progress.blocksToGo(), money(progress.averageBlockPay()), progress.averageBlockMinutes(),
                    date(progress.periodStart()), date(progress.periodEnd()));
        }
    }
}
