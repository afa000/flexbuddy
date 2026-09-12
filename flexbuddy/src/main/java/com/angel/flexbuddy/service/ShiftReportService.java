package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
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
import java.util.Objects;

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

@Service
public class ShiftReportService {
    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private final ShiftService shiftService;
    private final ExpenseService expenseService;
    private final AccountSettingsService settingsService;
    private final NetEarningsCalculator calculator;

    public ShiftReportService(ShiftService shiftService, ExpenseService expenseService,
            AccountSettingsService settingsService, NetEarningsCalculator calculator) {
        this.shiftService = shiftService;
        this.expenseService = expenseService;
        this.settingsService = settingsService;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true)
    public ShiftStatisticsResponse statistics(String email, ShiftFilter filter) {
        List<Shift> shifts = shiftService.findFiltered(email, filter);
        List<Expense> expenses = reportExpenses(email, filter);
        AccountSettingsResponse settings = settingsService.get(email);
        NetEarningsResult net = calculator.calculate(shifts, expenses, settings.vehicleCostMethod(), settings.mileageRate());
        BigDecimal base = sum(shifts, true);
        BigDecimal tips = sum(shifts, false);
        int count = shifts.size();
        return new ShiftStatisticsResponse(count, money(base), money(tips), net.grossEarnings(),
                divide(net.grossEarnings(), count), net.minutesWorked(), net.grossHourlyRate(),
                hourly(base, net.minutesWorked()), hourly(tips, net.minutesWorked()), divide(base, count),
                divide(tips, count), percentage(tips, net.grossEarnings()),
                count == 0 ? 0 : Math.round((float) net.minutesWorked() / count), net.miles(), net.mileageCost(),
                net.cashSpent(), net.totalDeductions(), net.netEarnings(), net.netHourlyRate(),
                net.earningsPerMile(), net.netMargin(), net.expenseTotals(), net.vehicleCost(),
                net.outOfPocketExpenses(), net.netPerShift(), settings.vehicleCostMethod(), settings.mileageRate());
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

    private List<Expense> reportExpenses(String email, ShiftFilter filter) {
        return expenseService.findFiltered(email, new ExpenseFilter(filter.from(), filter.to(), filter.station(),
                filter.query(), null, null));
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
        return shifts.stream().map(shift -> base ? shift.getBasePay() : shift.getTips()).filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    private BigDecimal money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP); }
    private BigDecimal divide(BigDecimal value, int divisor) { return divisor == 0 ? BigDecimal.ZERO.setScale(2) : value.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP); }
    private BigDecimal hourly(BigDecimal value, int minutes) { return minutes == 0 ? BigDecimal.ZERO.setScale(2) : value.multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(minutes), 2, RoundingMode.HALF_UP); }
    private BigDecimal percentage(BigDecimal part, BigDecimal whole) { return whole.signum() == 0 ? BigDecimal.ZERO.setScale(1) : part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP); }

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
                    net.mileageCost(), net.cashSpent(), net.totalDeductions(), net.netEarnings(), net.netHourlyRate());
        }
    }
}
