package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.dto.EarningsBucket;
import com.angel.flexbuddy.dto.EarningsReportResponse;
import com.angel.flexbuddy.dto.EarningsTotals;
import com.angel.flexbuddy.dto.ExpenseFilter;
import com.angel.flexbuddy.dto.GroupBy;
import com.angel.flexbuddy.dto.NetEarningsResult;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.ShiftStatisticsResponse;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;

@Service
public class ShiftReportService {
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private final ShiftService shiftService;
    private final ExpenseService expenseService;
    private final AccountSettingsService settingsService;
    private final NetEarningsCalculator calculator;
    private final UserTimeService userTime;
    private final GoalProgressCalculator goalCalculator;
    private final PayPeriodCalculator payPeriods;

    public ShiftReportService(ShiftService shiftService, ExpenseService expenseService,
            AccountSettingsService settingsService, NetEarningsCalculator calculator, UserTimeService userTime,
            GoalProgressCalculator goalCalculator, PayPeriodCalculator payPeriods) {
        this.shiftService = shiftService;
        this.expenseService = expenseService;
        this.settingsService = settingsService;
        this.calculator = calculator;
        this.userTime = userTime;
        this.goalCalculator = goalCalculator;
        this.payPeriods = payPeriods;
    }

    static final int MAX_PAY_PERIODS = 26;

    /** The pay period covering today and the {@code count - 1} before it, newest first, and the next payout. */
    @Transactional(readOnly = true)
    public com.angel.flexbuddy.dto.PayPeriodsResponse payPeriods(String email, int count) {
        if (count < 1 || count > MAX_PAY_PERIODS) {
            throw new com.angel.flexbuddy.exception.InvalidFilterException("count must be between 1 and 26.");
        }
        AccountSettingsResponse settings = settingsService.get(email);
        Set<DayOfWeek> days = Set.copyOf(settings.payoutDays());
        int lag = settings.payoutLagDays();
        LocalDate today = userTime.today(email);
        List<com.angel.flexbuddy.dto.PayPeriodResponse> periods = new ArrayList<>();
        PayPeriodCalculator.Period period = payPeriods.periodFor(today, days, lag);
        for (int index = 0; index < count; index++) {
            periods.add(payPeriod(email, period));
            period = payPeriods.previous(period, days, lag);
        }
        return new com.angel.flexbuddy.dto.PayPeriodsResponse(payPeriod(email, payPeriods.nextPayout(today, days, lag)), periods);
    }

    private com.angel.flexbuddy.dto.PayPeriodResponse payPeriod(String email, PayPeriodCalculator.Period period) {
        List<Shift> earned = shiftService.findFiltered(email, ShiftFilter.report(period.from(), period.to(), null, null));
        List<Shift> scheduled = shiftService.findScheduled(email, period.from(), period.to());
        return new com.angel.flexbuddy.dto.PayPeriodResponse(period.payoutDate(), period.from(), period.to(),
                earned.size(), money(earned.stream().map(Shift::getEarnedPay).reduce(BigDecimal.ZERO, BigDecimal::add)),
                scheduled.size(), money(scheduled.stream().map(Shift::getBasePay).reduce(BigDecimal.ZERO, BigDecimal::add)));
    }

    static final int GOAL_HISTORY_DAYS = 90;

    /** This week's and this month's goal progress on the driver's chosen basis; weeks run Monday to Sunday. */
    @Transactional(readOnly = true)
    public com.angel.flexbuddy.dto.GoalsResponse goals(String email) {
        AccountSettingsResponse settings = settingsService.get(email);
        if (settings.weeklyGoal() == null && settings.monthlyGoal() == null) {
            return new com.angel.flexbuddy.dto.GoalsResponse(settings.goalBasis(), null, null);
        }
        LocalDate today = userTime.today(email);
        boolean net = settings.goalBasis() == com.angel.flexbuddy.model.GoalBasis.NET;
        NetEarningsResult history = earningsBetween(email, today.minusDays(GOAL_HISTORY_DAYS - 1L), today, settings);
        List<Shift> recent = shiftService.findFiltered(email,
                ShiftFilter.report(today.minusDays(GOAL_HISTORY_DAYS - 1L), today, null, null)
                        .withStatuses(Set.of(ShiftStatus.COMPLETED)));
        int blocks = recent.size();
        BigDecimal averageBlockPay = blocks == 0 ? null
                : divide(net ? history.netEarnings() : history.grossEarnings(), blocks);
        Integer averageBlockMinutes = blocks == 0 ? null
                : Math.round((float) recent.stream().mapToInt(Shift::getWorkedMinutes).sum() / blocks);
        // Scheduled pay is gross, so on a net basis it is scaled by the share of pay usually kept.
        BigDecimal keptShare = !net || history.grossEarnings().signum() == 0 ? BigDecimal.ONE
                : history.netEarnings().divide(history.grossEarnings(), 4, RoundingMode.HALF_UP);

        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        YearMonth month = YearMonth.from(today);
        return new com.angel.flexbuddy.dto.GoalsResponse(settings.goalBasis(),
                goal(email, settings.weeklyGoal(), weekStart, weekStart.plusDays(6), today, net, keptShare,
                        averageBlockPay, averageBlockMinutes, settings),
                goal(email, settings.monthlyGoal(), month.atDay(1), month.atEndOfMonth(), today, net, keptShare,
                        averageBlockPay, averageBlockMinutes, settings));
    }

    private com.angel.flexbuddy.dto.GoalProgress goal(String email, BigDecimal target, LocalDate start, LocalDate end,
            LocalDate today, boolean net, BigDecimal keptShare, BigDecimal averageBlockPay, Integer averageBlockMinutes,
            AccountSettingsResponse settings) {
        if (target == null) return null;
        NetEarningsResult period = earningsBetween(email, start, end, settings);
        BigDecimal planned = shiftService.findScheduled(email, start, end).stream()
                .map(Shift::getBasePay).reduce(BigDecimal.ZERO, BigDecimal::add).multiply(keptShare);
        return goalCalculator.progress(target, net ? period.netEarnings() : period.grossEarnings(), planned,
                averageBlockPay, averageBlockMinutes, start, end, today);
    }

    private NetEarningsResult earningsBetween(String email, LocalDate from, LocalDate to, AccountSettingsResponse settings) {
        ShiftFilter filter = ShiftFilter.report(from, to, null, null);
        return calculator.calculate(shiftService.findFiltered(email, filter), reportExpenses(email, filter),
                settings.vehicleCostMethod(), settings.mileageRate());
    }

    @Transactional(readOnly = true)
    public ShiftStatisticsResponse statistics(String email, ShiftFilter filter) {
        List<Shift> shifts = shiftService.findFiltered(email, filter);
        LocalDateTime now = userTime.now(email);
        int rollingSevenDayMinutes = rollingSevenDayMinutes(email, now.toLocalDate());
        List<Expense> expenses = reportExpenses(email, filter);
        AccountSettingsResponse settings = settingsService.get(email);
        NetEarningsResult net = calculator.calculate(shifts, expenses, settings.vehicleCostMethod(), settings.mileageRate());
        BigDecimal base = sum(shifts, true);
        BigDecimal tips = sum(shifts, false);
        int count = shifts.size();
        int workedShifts = (int) shifts.stream().filter(Shift::countsTowardHours).count();
        ScheduleSnapshot schedule = scheduleSnapshot(email, filter, now);
        return new ShiftStatisticsResponse(count, money(base), money(tips), net.grossEarnings(),
                divide(net.grossEarnings(), count), net.minutesWorked(), net.grossHourlyRate(),
                hourly(base, net.minutesWorked()), hourly(tips, net.minutesWorked()), divide(base, count),
                divide(tips, count), percentage(tips, net.grossEarnings()),
                workedShifts == 0 ? 0 : Math.round((float) net.minutesWorked() / workedShifts), net.miles(),
                net.mileageCost(), net.cashSpent(), net.totalDeductions(), net.netEarnings(), net.netHourlyRate(),
                net.earningsPerMile(), net.netMargin(), net.expenseTotals(), net.vehicleCost(),
                net.outOfPocketExpenses(), net.netPerShift(), settings.vehicleCostMethod(), settings.mileageRate(),
                rollingSevenDayMinutes, schedule.plannedShifts(), schedule.plannedMinutes(), schedule.expectedPay(),
                schedule.needsConfirmation(), schedule.cancelled(), schedule.forfeited(),
                schedule.forfeitedThisMonth(), timed(shifts).size(), clockedMinutes(shifts),
                hourly(net.grossEarnings(), clockedMinutes(shifts)), averageFinishedEarly(shifts),
                schedule.lateForfeited(), schedule.lateForfeitedThisMonth());
    }

    /** Worked blocks with both actual times recorded. */
    private static List<Shift> timed(List<Shift> shifts) {
        return shifts.stream().filter(shift -> shift.getFinishedEarlyMinutes() != null).toList();
    }

    /** Worked blocks with stops recorded; route averages ignore every other block. */
    private static List<Shift> routeShifts(List<Shift> shifts) {
        return shifts.stream().filter(shift -> shift.getMinutesPerStop() != null).toList();
    }

    private static BigDecimal averageStops(List<Shift> shifts) {
        List<Shift> route = routeShifts(shifts);
        return route.isEmpty() ? null : BigDecimal.valueOf(route.stream().mapToInt(Shift::getStops).sum())
                .divide(BigDecimal.valueOf(route.size()), 1, RoundingMode.HALF_UP);
    }

    private static BigDecimal averageMinutesPerStop(List<Shift> shifts) {
        List<Shift> route = routeShifts(shifts);
        return route.isEmpty() ? null : BigDecimal.valueOf(route.stream().mapToInt(Shift::getClockedMinutes).sum())
                .divide(BigDecimal.valueOf(route.stream().mapToInt(Shift::getStops).sum()), 1, RoundingMode.HALF_UP);
    }

    /** Returns as a percentage of everything carried, over blocks that recorded both. */
    private static BigDecimal returnsRate(List<Shift> shifts) {
        List<Shift> counted = shifts.stream().filter(shift -> shift.getReturnsBase() != null).toList();
        if (counted.isEmpty()) return null;
        long returned = counted.stream().mapToLong(Shift::getReturns).sum();
        long carried = counted.stream().mapToLong(Shift::getReturnsBase).sum();
        return BigDecimal.valueOf(returned * 100).divide(BigDecimal.valueOf(carried), 1, RoundingMode.HALF_UP);
    }

    private static int clockedMinutes(List<Shift> shifts) {
        return shifts.stream().mapToInt(Shift::getClockedMinutes).sum();
    }

    private static Integer averageFinishedEarly(List<Shift> shifts) {
        List<Shift> timed = timed(shifts);
        return timed.isEmpty() ? null
                : Math.round((float) timed.stream().mapToInt(Shift::getFinishedEarlyMinutes).sum() / timed.size());
    }

    private int rollingSevenDayMinutes(String email, LocalDate today) {
        ShiftFilter rollingWindow = ShiftFilter.report(today.minusDays(6), today, null, null);
        return shiftService.findFiltered(email, rollingWindow).stream()
                .mapToInt(Shift::getWorkedMinutes)
                .sum();
    }

    /** Planned work for the next seven days in the driver's zone, plus unworked blocks for the dashboard tiles. */
    private ScheduleSnapshot scheduleSnapshot(String email, ShiftFilter filter, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        List<Shift> planned = shiftService.findScheduled(email, today, today.plusDays(6)).stream()
                .filter(shift -> shift.getEndDateTime().isAfter(now))
                .toList();
        int needsConfirmation = (int) shiftService.findScheduled(email, null, today).stream()
                .filter(shift -> !shift.getEndDateTime().isAfter(now))
                .count();
        List<Shift> unworked = shiftService.findFiltered(email,
                filter.withStatuses(Set.of(ShiftStatus.CANCELLED, ShiftStatus.FORFEITED)));
        YearMonth month = YearMonth.from(today);
        List<Shift> forfeitedThisMonth = shiftService.findFiltered(email,
                ShiftFilter.report(month.atDay(1), month.atEndOfMonth(), null, null)
                        .withStatuses(Set.of(ShiftStatus.FORFEITED)));
        return new ScheduleSnapshot(planned.size(), planned.stream().mapToInt(Shift::getTimeWorked).sum(),
                money(planned.stream().map(Shift::getBasePay).reduce(BigDecimal.ZERO, BigDecimal::add)),
                needsConfirmation, count(unworked, ShiftStatus.CANCELLED), count(unworked, ShiftStatus.FORFEITED),
                forfeitedThisMonth.size(), late(unworked), late(forfeitedThisMonth));
    }

    @Transactional(readOnly = true)
    public EarningsReportResponse earnings(String email, ShiftFilter filter, GroupBy groupBy) {
        List<Shift> shifts = shiftService.findFiltered(email, filter);
        List<Expense> expenses = reportExpenses(email, filter);
        AccountSettingsResponse settings = settingsService.get(email);
        Map<String, GroupAccumulator> groups = new LinkedHashMap<>();
        shifts.forEach(shift -> addShift(groups, shift, groupBy));
        expenses.forEach(expense -> addExpense(groups, expense, groupBy));
        List<EarningsBucket> buckets = new ArrayList<>(groups.values().stream()
                .map(group -> group.bucket(settings)).toList());
        if (groupBy == GroupBy.STATION) buckets.sort(Comparator.comparing(EarningsBucket::netEarnings).reversed()
                .thenComparing(EarningsBucket::label, String.CASE_INSENSITIVE_ORDER));
        else buckets.sort(Comparator.comparing(EarningsBucket::periodStart, Comparator.nullsLast(Comparator.naturalOrder())));
        NetEarningsResult total = calculator.calculate(shifts, expenses, settings.vehicleCostMethod(), settings.mileageRate());
        EarningsTotals totals = new EarningsTotals(shifts.size(), money(sum(shifts, true)), money(sum(shifts, false)),
                total.grossEarnings(), total.minutesWorked(), total.grossHourlyRate(), total.miles(), total.mileageCost(),
                total.cashSpent(), total.totalDeductions(), total.netEarnings(), total.netHourlyRate());
        return new EarningsReportResponse(groupBy.name().toLowerCase(Locale.ROOT), filter.from(), filter.to(), buckets, totals);
    }

    private void addShift(Map<String, GroupAccumulator> groups, Shift shift, GroupBy groupBy) {
        GroupKey key = groupKey(shift.getDate(), shift.getStation(), groupBy);
        groups.computeIfAbsent(key.key(), ignored -> new GroupAccumulator(key)).shifts.add(shift);
    }

    private void addExpense(Map<String, GroupAccumulator> groups, Expense expense, GroupBy groupBy) {
        String station = expense.getShift() == null ? null : expense.getShift().getStation();
        GroupKey key = groupKey(expense.getDate(), station, groupBy);
        groups.computeIfAbsent(key.key(), ignored -> new GroupAccumulator(key)).expenses.add(expense);
    }

    /** Expenses linked to a block that has not happened yet are not counted. */
    private List<Expense> reportExpenses(String email, ShiftFilter filter) {
        return expenseService.findFiltered(email, new ExpenseFilter(filter.from(), filter.to(), filter.station(),
                filter.query(), null, null)).stream()
                .filter(expense -> expense.getShift() == null || expense.getShift().getStatus() != ShiftStatus.SCHEDULED)
                .toList();
    }

    private GroupKey groupKey(LocalDate date, String station, GroupBy groupBy) {
        if (groupBy == GroupBy.STATION) {
            String value = station == null || station.isBlank() ? "Unlinked expenses" : station.trim();
            return new GroupKey(value.toLowerCase(Locale.ROOT), value, null, null);
        }
        if (date == null) return new GroupKey("unknown", "Unknown date", null, null);
        return switch (groupBy) {
            case WEEK -> {
                LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                yield new GroupKey(String.format("%d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR),
                        date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)), "Week of " + WEEK_LABEL.format(start), start, start.plusDays(6));
            }
            case MONTH -> {
                YearMonth month = YearMonth.from(date);
                yield new GroupKey(month.toString(), MONTH_LABEL.format(month), month.atDay(1), month.atEndOfMonth());
            }
            case YEAR -> new GroupKey(String.valueOf(date.getYear()), String.valueOf(date.getYear()),
                    LocalDate.of(date.getYear(), 1, 1), LocalDate.of(date.getYear(), 12, 31));
            case STATION -> throw new IllegalStateException();
        };
    }

    private BigDecimal sum(List<Shift> shifts, boolean base) {
        return shifts.stream().map(shift -> base ? shift.getEarnedBasePay() : shift.getEarnedTips())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private static int late(List<Shift> shifts) {
        return (int) shifts.stream().filter(shift -> shift.getStatus() == ShiftStatus.FORFEITED && shift.isLateForfeit()).count();
    }
    private int count(List<Shift> shifts, ShiftStatus status) { return (int) shifts.stream().filter(shift -> shift.getStatus() == status).count(); }
    private BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal divide(BigDecimal value, int divisor) { return divisor == 0 ? BigDecimal.ZERO.setScale(2) : value.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP); }
    private BigDecimal hourly(BigDecimal value, int minutes) { return minutes == 0 ? BigDecimal.ZERO.setScale(2) : value.multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(minutes), 2, RoundingMode.HALF_UP); }
    private BigDecimal percentage(BigDecimal part, BigDecimal whole) { return whole.signum() == 0 ? BigDecimal.ZERO.setScale(1) : part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP); }

    private record ScheduleSnapshot(int plannedShifts, int plannedMinutes, BigDecimal expectedPay,
            int needsConfirmation, int cancelled, int forfeited, int forfeitedThisMonth, int lateForfeited,
            int lateForfeitedThisMonth) {}
    private record GroupKey(String key, String label, LocalDate start, LocalDate end) {}
    private final class GroupAccumulator {
        private final GroupKey key;
        private final List<Shift> shifts = new ArrayList<>();
        private final List<Expense> expenses = new ArrayList<>();
        private GroupAccumulator(GroupKey key) { this.key = key; }
        private EarningsBucket bucket(AccountSettingsResponse settings) {
            NetEarningsResult net = calculator.calculate(shifts, expenses, settings.vehicleCostMethod(), settings.mileageRate());
            return new EarningsBucket(key.key(), key.label(), key.start(), key.end(), shifts.size(), money(sum(shifts, true)),
                    money(sum(shifts, false)), net.grossEarnings(), net.minutesWorked(), net.grossHourlyRate(), net.miles(),
                    net.mileageCost(), net.cashSpent(), net.totalDeductions(), net.netEarnings(), net.netHourlyRate(),
                    timed(shifts).size(), clockedMinutes(shifts), hourly(net.grossEarnings(), clockedMinutes(shifts)),
                    averageFinishedEarly(shifts), routeShifts(shifts).size(), averageStops(shifts),
                    averageMinutesPerStop(shifts), returnsRate(shifts));
        }
    }
}
