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

import com.angel.flexbuddy.dto.EarningsBucket;
import com.angel.flexbuddy.dto.EarningsReportResponse;
import com.angel.flexbuddy.dto.EarningsTotals;
import com.angel.flexbuddy.dto.GroupBy;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.ShiftStatisticsResponse;
import com.angel.flexbuddy.model.Shift;

@Service
public class ShiftReportService {

    private static final DateTimeFormatter MONTH_LABEL = DateTimeFormatter.ofPattern("MMM uuuu", Locale.ENGLISH);
    private static final DateTimeFormatter WEEK_LABEL = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private final ShiftService shiftService;

    public ShiftReportService(ShiftService shiftService) {
        this.shiftService = shiftService;
    }

    public ShiftStatisticsResponse statistics(String email, ShiftFilter filter) {
        EarningsAccumulator total = accumulate(shiftService.findFiltered(email, filter));
        int count = total.shifts;
        return new ShiftStatisticsResponse(
                count, total.basePay, total.tips, total.total(), divide(total.total(), count), total.minutes,
                hourly(total.total(), total.minutes), hourly(total.basePay, total.minutes),
                hourly(total.tips, total.minutes), divide(total.basePay, count), divide(total.tips, count),
                percentage(total.tips, total.total()), count == 0 ? 0 : Math.round((float) total.minutes / count)
        );
    }

    public EarningsReportResponse earnings(String email, ShiftFilter filter, GroupBy groupBy) {
        List<Shift> shifts = shiftService.findFiltered(email, filter);
        Map<String, EarningsAccumulator> grouped = new LinkedHashMap<>();
        EarningsAccumulator totals = new EarningsAccumulator();

        for (Shift shift : shifts) {
            GroupKey group = groupKey(shift, groupBy);
            grouped.computeIfAbsent(group.key, ignored -> new EarningsAccumulator(group)).add(shift);
            totals.add(shift);
        }

        List<EarningsBucket> buckets = new ArrayList<>(grouped.values().stream()
                .map(EarningsAccumulator::bucket)
                .toList());
        if (groupBy == GroupBy.STATION) {
            buckets.sort(Comparator.comparing(EarningsBucket::totalEarnings).reversed()
                    .thenComparing(EarningsBucket::label, String.CASE_INSENSITIVE_ORDER));
        } else {
            buckets.sort(Comparator.comparing(EarningsBucket::periodStart));
        }

        return new EarningsReportResponse(groupBy.name().toLowerCase(Locale.ROOT), filter.from(), filter.to(),
                buckets, totals.totals());
    }

    private EarningsAccumulator accumulate(List<Shift> shifts) {
        EarningsAccumulator total = new EarningsAccumulator();
        shifts.forEach(total::add);
        return total;
    }

    private GroupKey groupKey(Shift shift, GroupBy groupBy) {
        LocalDate date = shift.getDate();
        return switch (groupBy) {
            case STATION -> {
                String station = Objects.requireNonNullElse(shift.getStation(), "").trim();
                yield new GroupKey(station.toLowerCase(Locale.ROOT), station, null, null);
            }
            case WEEK -> {
                LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                int weekYear = date.get(IsoFields.WEEK_BASED_YEAR);
                int week = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
                yield new GroupKey(String.format("%d-W%02d", weekYear, week), "Week of " + WEEK_LABEL.format(start),
                        start, start.plusDays(6));
            }
            case MONTH -> {
                YearMonth month = YearMonth.from(date);
                yield new GroupKey(month.toString(), MONTH_LABEL.format(month), month.atDay(1), month.atEndOfMonth());
            }
            case YEAR -> new GroupKey(String.valueOf(date.getYear()), String.valueOf(date.getYear()),
                    LocalDate.of(date.getYear(), 1, 1), LocalDate.of(date.getYear(), 12, 31));
        };
    }

    private static BigDecimal money(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.setScale(2) : value;
    }

    private static BigDecimal divide(BigDecimal value, int divisor) {
        return divisor == 0 ? BigDecimal.ZERO.setScale(2)
                : value.divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal hourly(BigDecimal value, int minutes) {
        return minutes == 0 ? BigDecimal.ZERO.setScale(2)
                : value.multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(minutes), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal percentage(BigDecimal part, BigDecimal whole) {
        return whole.signum() == 0 ? BigDecimal.ZERO.setScale(1)
                : part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP);
    }

    private record GroupKey(String key, String label, LocalDate start, LocalDate end) {}

    private static final class EarningsAccumulator {
        private final GroupKey group;
        private int shifts;
        private BigDecimal basePay = BigDecimal.ZERO.setScale(2);
        private BigDecimal tips = BigDecimal.ZERO.setScale(2);
        private int minutes;

        private EarningsAccumulator() { this(null); }
        private EarningsAccumulator(GroupKey group) { this.group = group; }

        private void add(Shift shift) {
            shifts++;
            basePay = basePay.add(money(shift.getBasePay()));
            tips = tips.add(money(shift.getTips()));
            minutes += shift.getTimeWorked();
        }

        private BigDecimal total() { return basePay.add(tips); }
        private EarningsTotals totals() {
            return new EarningsTotals(shifts, basePay, tips, total(), minutes, hourly(total(), minutes));
        }
        private EarningsBucket bucket() {
            return new EarningsBucket(group.key, group.label, group.start, group.end, shifts, basePay, tips, total(),
                    minutes, hourly(total(), minutes));
        }
    }
}

