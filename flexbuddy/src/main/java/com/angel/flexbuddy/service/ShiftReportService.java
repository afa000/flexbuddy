package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.context.i18n.LocaleContextHolder;
import com.angel.flexbuddy.i18n.Messages;
import com.angel.flexbuddy.i18n.UserLocales;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.importcore.figures.EarningsReport;
import com.angel.flexbuddy.importcore.figures.GoalPreparation;
import com.angel.flexbuddy.importcore.figures.NetEarnings;
import com.angel.flexbuddy.importcore.figures.PlannedWork;
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
    private final ShiftService shiftService;
    private final ExpenseService expenseService;
    private final AccountSettingsService settingsService;
    private final NetEarningsCalculator calculator;
    private final UserTimeService userTime;
    private final GoalProgressCalculator goalCalculator;
    private final PayPeriodCalculator payPeriods;
    private final com.angel.flexbuddy.repository.PayoutDepositRepository deposits;

    public ShiftReportService(ShiftService shiftService, ExpenseService expenseService,
            AccountSettingsService settingsService, NetEarningsCalculator calculator, UserTimeService userTime,
            GoalProgressCalculator goalCalculator, PayPeriodCalculator payPeriods,
            com.angel.flexbuddy.repository.PayoutDepositRepository deposits) {
        this.shiftService = shiftService;
        this.expenseService = expenseService;
        this.settingsService = settingsService;
        this.calculator = calculator;
        this.userTime = userTime;
        this.goalCalculator = goalCalculator;
        this.payPeriods = payPeriods;
        this.deposits = deposits;
    }

    /** Start-time bands for the heatmap: before 8, 8 to 11, 11 to 2, 2 to 5, and 5 onward, by the block's start hour. */
    static final int[] HEATMAP_BAND_STARTS = {0, 8, 11, 14, 17};
    private static List<String> heatmapBands() {
        return List.of(Messages.current("report.band.before8"), Messages.current("report.band.8to11"),
                Messages.current("report.band.11to2"), Messages.current("report.band.2to5"),
                Messages.current("report.band.after5"));
    }
    static final int HEATMAP_MIN_SHIFTS = 2;

    /**
     * Which weekday and time of day pays best. Only worked blocks count, and a cell's net uses its blocks' own miles
     * and linked expenses, since fuel bought separately cannot be tied to a time of day.
     */
    @Transactional(readOnly = true)
    public com.angel.flexbuddy.dto.HeatmapResponse heatmap(String email, ShiftFilter filter,
            com.angel.flexbuddy.dto.HeatmapMetric metric) {
        List<Shift> shifts = shiftService.findFiltered(email, filter.withStatuses(Set.of(ShiftStatus.COMPLETED)));
        AccountSettingsResponse settings = settingsService.get(email);
        Map<Long, List<Expense>> linked = new java.util.HashMap<>();
        expenseService.findForShifts(email, shifts)
                .forEach(expense -> linked.computeIfAbsent(expense.getShift().getId(), ignored -> new ArrayList<>()).add(expense));
        Map<String, List<Shift>> groups = new java.util.TreeMap<>();
        for (Shift shift : shifts) {
            groups.computeIfAbsent(shift.getDate().getDayOfWeek().getValue() + ":" + band(shift.getStartTime().getHour()),
                    ignored -> new ArrayList<>()).add(shift);
        }
        List<com.angel.flexbuddy.dto.HeatmapCell> cells = new ArrayList<>();
        for (Map.Entry<String, List<Shift>> group : groups.entrySet()) {
            String[] key = group.getKey().split(":");
            List<Shift> cellShifts = group.getValue();
            List<Expense> cellExpenses = cellShifts.stream()
                    .flatMap(shift -> linked.getOrDefault(shift.getId(), List.of()).stream()).toList();
            NetEarningsResult net = calculator.calculate(cellShifts, cellExpenses, settings.vehicleCostMethod(), settings.mileageRates());
            BigDecimal value = switch (metric) {
                case NET_HOURLY -> net.netHourlyRate();
                case GROSS_HOURLY -> net.grossHourlyRate();
                case SHIFTS -> BigDecimal.valueOf(cellShifts.size());
                case AVERAGE_PAY -> divide(net.grossEarnings(), cellShifts.size());
            };
            cells.add(new com.angel.flexbuddy.dto.HeatmapCell(Integer.parseInt(key[0]), Integer.parseInt(key[1]),
                    cellShifts.size(), net.minutesWorked(), value, cellShifts.size() < HEATMAP_MIN_SHIFTS));
        }
        List<BigDecimal> values = cells.stream().filter(cell -> !cell.sparse()).map(com.angel.flexbuddy.dto.HeatmapCell::value)
                .sorted().toList();
        com.angel.flexbuddy.dto.HeatmapCell best = cells.stream().filter(cell -> !cell.sparse())
                .max(Comparator.comparing(com.angel.flexbuddy.dto.HeatmapCell::value)
                        .thenComparingInt(com.angel.flexbuddy.dto.HeatmapCell::shifts))
                .orElse(null);
        return new com.angel.flexbuddy.dto.HeatmapResponse(metric, heatmapBands(), cells, best, scale(values), shifts.size());
    }

    private static int band(int hour) {
        int band = 0;
        for (int index = 0; index < HEATMAP_BAND_STARTS.length; index++) {
            if (hour >= HEATMAP_BAND_STARTS[index]) band = index;
        }
        return band;
    }

    /** Upper bounds of up to five equal-count colour steps, so a single outlier does not flatten the rest. */
    private static List<BigDecimal> scale(List<BigDecimal> sorted) {
        if (sorted.isEmpty()) return List.of();
        List<BigDecimal> bounds = new ArrayList<>();
        int steps = Math.min(5, sorted.size());
        for (int step = 1; step <= steps; step++) {
            BigDecimal bound = sorted.get((int) Math.ceil(step * sorted.size() / (double) steps) - 1);
            if (bounds.isEmpty() || bound.compareTo(bounds.getLast()) > 0) bounds.add(bound);
        }
        return bounds;
    }

    static final int MAX_PAY_PERIODS = 26;

    /** The pay period covering today and the {@code count - 1} before it, newest first, and the next payout. */
    @Transactional(readOnly = true)
    public com.angel.flexbuddy.dto.PayPeriodsResponse payPeriods(String email, int count) {
        if (count < 1 || count > MAX_PAY_PERIODS) {
            throw new com.angel.flexbuddy.exception.InvalidFilterException("error.filter.countRange");
        }
        AccountSettingsResponse settings = settingsService.get(email);
        Set<DayOfWeek> days = Set.copyOf(settings.payoutDays());
        int lag = settings.payoutLagDays();
        LocalDate today = userTime.today(email);
        List<PayPeriodCalculator.Period> spans = new ArrayList<>();
        PayPeriodCalculator.Period period = payPeriods.periodFor(today, days, lag);
        for (int index = 0; index < count; index++) {
            spans.add(period);
            period = payPeriods.previous(period, days, lag);
        }
        PayPeriodCalculator.Period next = payPeriods.nextPayout(today, days, lag);
        Map<LocalDate, com.angel.flexbuddy.model.PayoutDeposit> received = new java.util.HashMap<>();
        deposits.findByOwnerEmailIgnoreCaseAndPayoutDateBetween(email, spans.getLast().payoutDate(), spans.getFirst().payoutDate())
                .forEach(deposit -> received.put(deposit.getPayoutDate(), deposit));
        List<com.angel.flexbuddy.dto.PayPeriodResponse> periods = spans.stream()
                .map(span -> payPeriod(email, span, received.get(span.payoutDate()), today)).toList();
        return new com.angel.flexbuddy.dto.PayPeriodsResponse(payPeriod(email, next, received.get(next.payoutDate()), today), periods);
    }

    private com.angel.flexbuddy.dto.PayPeriodResponse payPeriod(String email, PayPeriodCalculator.Period period,
            com.angel.flexbuddy.model.PayoutDeposit deposit, LocalDate today) {
        List<Shift> worked = shiftService.findFiltered(email, ShiftFilter.report(period.from(), period.to(), null, null));
        List<Shift> scheduled = shiftService.findScheduled(email, period.from(), period.to());
        BigDecimal earned = money(worked.stream().map(Shift::getEarnedPay).reduce(BigDecimal.ZERO, BigDecimal::add));
        BigDecimal difference = deposit == null ? null : money(deposit.getAmount().subtract(earned));
        return new com.angel.flexbuddy.dto.PayPeriodResponse(period.payoutDate(), period.from(), period.to(),
                worked.size(), earned,
                scheduled.size(), money(scheduled.stream().map(Shift::getBasePay).reduce(BigDecimal.ZERO, BigDecimal::add)),
                deposit == null ? null : money(deposit.getAmount()), difference, deposit == null ? null : deposit.getNote(),
                payoutStatus(period.payoutDate(), today, difference));
    }

    private static com.angel.flexbuddy.dto.PayoutStatus payoutStatus(LocalDate payoutDate, LocalDate today, BigDecimal difference) {
        if (difference != null) {
            // Amounts are in cents, so anything under a cent either way is a match.
            if (difference.abs().compareTo(new BigDecimal("0.01")) < 0) return com.angel.flexbuddy.dto.PayoutStatus.MATCHED;
            return difference.signum() < 0 ? com.angel.flexbuddy.dto.PayoutStatus.SHORT : com.angel.flexbuddy.dto.PayoutStatus.OVER;
        }
        return payoutDate.isAfter(today) ? com.angel.flexbuddy.dto.PayoutStatus.UPCOMING : com.angel.flexbuddy.dto.PayoutStatus.UNCHECKED;
    }

    /** This week's and this month's goal progress on the driver's chosen basis; weeks run Monday to Sunday. */
    @Transactional(readOnly = true)
    public com.angel.flexbuddy.dto.GoalsResponse goals(String email) {
        AccountSettingsResponse settings = settingsService.get(email);
        if (settings.weeklyGoal() == null && settings.monthlyGoal() == null) {
            return new com.angel.flexbuddy.dto.GoalsResponse(settings.goalBasis(), null, null);
        }
        LocalDate today = userTime.today(email);
        LocalDate historyStart = GoalPreparation.historyStart(today);
        NetEarnings history = earningsBetween(email, historyStart, today, settings);
        List<Shift> recent = shiftService.findFiltered(email,
                ShiftFilter.report(historyStart, today, null, null).withStatuses(Set.of(ShiftStatus.COMPLETED)));
        GoalPreparation preparation = GoalPreparation.fromHistory(history, FigureInputs.blocks(recent),
                settings.goalBasis() == com.angel.flexbuddy.model.GoalBasis.NET);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        YearMonth month = YearMonth.from(today);
        return new com.angel.flexbuddy.dto.GoalsResponse(settings.goalBasis(),
                goal(email, settings.weeklyGoal(), weekStart, weekStart.plusDays(6), today, preparation, settings),
                goal(email, settings.monthlyGoal(), month.atDay(1), month.atEndOfMonth(), today, preparation, settings));
    }

    private com.angel.flexbuddy.dto.GoalProgress goal(String email, BigDecimal target, LocalDate start, LocalDate end,
            LocalDate today, GoalPreparation preparation, AccountSettingsResponse settings) {
        if (target == null) return null;
        NetEarnings period = earningsBetween(email, start, end, settings);
        BigDecimal planned = preparation.plannedPay(FigureInputs.blocks(shiftService.findScheduled(email, start, end)));
        return goalCalculator.progress(target, preparation.earned(period), planned,
                preparation.averageBlockPay(), preparation.averageBlockMinutes(), start, end, today);
    }

    private NetEarnings earningsBetween(String email, LocalDate from, LocalDate to, AccountSettingsResponse settings) {
        ShiftFilter filter = ShiftFilter.report(from, to, null, null);
        return calculator.figures(shiftService.findFiltered(email, filter), reportExpenses(email, filter),
                settings.vehicleCostMethod(), settings.mileageRates());
    }

    @Transactional(readOnly = true)
    public ShiftStatisticsResponse statistics(String email, ShiftFilter filter) {
        List<Shift> shifts = shiftService.findFiltered(email, filter);
        LocalDateTime now = userTime.now(email);
        int rollingSevenDayMinutes = rollingSevenDayMinutes(email, now.toLocalDate());
        List<Expense> expenses = reportExpenses(email, filter);
        AccountSettingsResponse settings = settingsService.get(email);
        NetEarningsResult net = calculator.calculate(shifts, expenses, settings.vehicleCostMethod(), settings.mileageRates());
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
                schedule.lateForfeited(), schedule.lateForfeitedThisMonth(), net.blocksWithoutMiles());
    }

    /** Worked blocks with both actual times recorded. */
    private static List<Shift> timed(List<Shift> shifts) {
        return shifts.stream().filter(shift -> shift.getFinishedEarlyMinutes() != null).toList();
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
        PlannedWork planned = PlannedWork.of(
                FigureInputs.blocks(shiftService.findScheduled(email, today, today.plusDays(6))),
                FigureInputs.blocks(shiftService.findScheduled(email, null, today)), now);
        List<Shift> unworked = shiftService.findFiltered(email,
                filter.withStatuses(Set.of(ShiftStatus.CANCELLED, ShiftStatus.FORFEITED)));
        YearMonth month = YearMonth.from(today);
        List<Shift> forfeitedThisMonth = shiftService.findFiltered(email,
                ShiftFilter.report(month.atDay(1), month.atEndOfMonth(), null, null)
                        .withStatuses(Set.of(ShiftStatus.FORFEITED)));
        return new ScheduleSnapshot(planned.plannedShifts(), planned.plannedMinutes(), planned.expectedPay(),
                planned.needsConfirmation(), count(unworked, ShiftStatus.CANCELLED), count(unworked, ShiftStatus.FORFEITED),
                forfeitedThisMonth.size(), late(unworked), late(forfeitedThisMonth));
    }

    @Transactional(readOnly = true)
    public EarningsReportResponse earnings(String email, ShiftFilter filter, GroupBy groupBy) {
        List<Shift> shifts = shiftService.findFiltered(email, filter);
        List<Expense> expenses = reportExpenses(email, filter);
        AccountSettingsResponse settings = settingsService.get(email);
        Map<Long, ExpenseService.LinkedShift> links = expenseService.linkedShifts(email, expenses);
        EarningsReport report = EarningsReport.calculate(FigureInputs.reportBlocks(shifts),
                FigureInputs.reportCosts(expenses, links), FigureInputs.groupBy(groupBy),
                FigureInputs.method(settings.vehicleCostMethod()), settings.mileageRates().figures(),
                Messages.current("report.unlinkedExpenses"));
        List<EarningsBucket> buckets = new ArrayList<>(report.buckets().size());
        for (EarningsReport.Bucket bucket : report.buckets()) buckets.add(bucket(bucket, groupBy));
        EarningsReport.Figures total = report.totals();
        EarningsTotals totals = new EarningsTotals(total.shifts(), total.basePay(), total.tips(),
                total.totalEarnings(), total.minutesWorked(), total.hourlyRate(), total.miles(), total.mileageCost(),
                total.expenses(), total.deductions(), total.netEarnings(), total.netHourlyRate());
        return new EarningsReportResponse(groupBy.name().toLowerCase(Locale.ROOT), filter.from(), filter.to(), buckets, totals);
    }

    private static EarningsBucket bucket(EarningsReport.Bucket bucket, GroupBy groupBy) {
        EarningsReport.Figures figures = bucket.figures();
        return new EarningsBucket(bucket.key(), label(bucket, groupBy), bucket.periodStart(), bucket.periodEnd(),
                figures.shifts(), figures.basePay(), figures.tips(), figures.totalEarnings(), figures.minutesWorked(),
                figures.hourlyRate(), figures.miles(), figures.mileageCost(), figures.expenses(), figures.deductions(),
                figures.netEarnings(), figures.netHourlyRate(), figures.timedShifts(), figures.clockedMinutes(),
                figures.clockedHourlyRate(), figures.averageFinishedEarlyMinutes(), figures.shiftsWithRouteData(),
                figures.averageStops(), figures.averageMinutesPerStop(), figures.returnsRate(),
                figures.netMissingMiles());
    }

    /** The bucket's name in the request's language; the library's buckets carry none. */
    private static String label(EarningsReport.Bucket bucket, GroupBy groupBy) {
        Locale locale = UserLocales.clamp(LocaleContextHolder.getLocale());
        return switch (bucket.kind()) {
            case STATION -> bucket.station();
            case UNLINKED -> Messages.current("report.unlinkedExpenses");
            case UNKNOWN_DATE -> Messages.current("report.unknownDate");
            case PERIOD -> switch (groupBy) {
                case WEEK -> Messages.current("report.weekOf", DateTimeFormatter.ofPattern(
                        Messages.current("format.reportDay"), locale).format(bucket.periodStart()));
                case MONTH -> MONTH_LABEL.withLocale(locale).format(YearMonth.from(bucket.periodStart()));
                case YEAR -> String.valueOf(bucket.periodStart().getYear());
                case STATION -> throw new IllegalStateException();
            };
        };
    }

    /** Expenses linked to a block that has not happened yet are not counted; ones on a deleted block count by date. */
    private List<Expense> reportExpenses(String email, ShiftFilter filter) {
        return expenseService.countable(email, expenseService.findFiltered(email, new ExpenseFilter(filter.from(),
                filter.to(), filter.station(), filter.query(), null, null)));
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
}
