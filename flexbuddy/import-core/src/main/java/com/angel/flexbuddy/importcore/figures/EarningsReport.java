package com.angel.flexbuddy.importcore.figures;

import static com.angel.flexbuddy.importcore.figures.Rounding.hourly;
import static com.angel.flexbuddy.importcore.figures.Rounding.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The earnings report: blocks and expenses grouped by week, month, year or station, with each group's figures and the
 * totals over everything. The consumer selects the rows first (only the signed-in account's live blocks and
 * expenses; see {@link ReportSelection}); expenses that wait for a scheduled block are left out here too.
 *
 * <p>Buckets carry a stable key and a kind but no display label: the consumer names each bucket in its own language.
 * Weeks run Monday to Sunday and are keyed by ISO week-based year and week ({@code 2026-W53}); months are
 * {@code yyyy-MM}, years {@code yyyy}. A row with no date goes to the {@link Kind#UNKNOWN_DATE} bucket. Stations group
 * case-insensitively on the trimmed station, keyed in lower case, and a station bucket's {@code station} is the
 * trimmed station of the first row added to it (blocks before expenses, each in the order given). Expenses with no
 * counted station, and blocks with a blank station, go to the {@link Kind#UNLINKED} bucket.
 *
 * <p>Period buckets are ordered by period start, the unknown-date bucket last. Station buckets are ordered by net
 * descending, then by label case-insensitively, where the label is the bucket's station, or for the unlinked bucket
 * the consumer's own {@code unlinkedLabel}. That keeps the order identical to the website's, which sorts on its
 * localized label. Buckets still equal keep the order in which they were first seen.
 */
public record EarningsReport(List<Bucket> buckets, Figures totals) {

    /** The key of the bucket for expenses with no counted station. */
    public static final String UNLINKED_KEY = "unlinked expenses";
    /** The key of the bucket for rows with no date. */
    public static final String UNKNOWN_DATE_KEY = "unknown";

    /** How rows are grouped, with the same names and order as the server's. */
    public enum GroupBy {
        STATION, WEEK, MONTH, YEAR
    }

    public enum Kind {
        PERIOD, STATION, UNLINKED, UNKNOWN_DATE
    }

    /**
     * One group. {@code periodStart} and {@code periodEnd} are set for a period bucket only, {@code station} for a
     * station bucket only.
     */
    public record Bucket(String key, Kind kind, LocalDate periodStart, LocalDate periodEnd, String station,
            Figures figures) {
    }

    /**
     * A group's figures, with the website's scales. Gross, minutes, miles, costs and net come from
     * {@link NetEarnings}; {@code expenses} is the cash spent. Clocked time and early finishes count worked blocks
     * (see {@link RouteRules}); the route averages count only blocks with stops recorded, and the returns rate only
     * blocks with a returns base. {@code netMissingMiles} counts worked blocks with no miles, and is 0 under actual
     * expenses, where miles don't change the net.
     */
    public record Figures(int shifts, BigDecimal basePay, BigDecimal tips, BigDecimal totalEarnings, int minutesWorked,
            BigDecimal hourlyRate, BigDecimal miles, BigDecimal mileageCost, BigDecimal expenses,
            BigDecimal deductions, BigDecimal netEarnings, BigDecimal netHourlyRate, int timedShifts,
            int clockedMinutes, BigDecimal clockedHourlyRate, Integer averageFinishedEarlyMinutes,
            int shiftsWithRouteData, BigDecimal averageStops, BigDecimal averageMinutesPerStop,
            BigDecimal returnsRate, int netMissingMiles) {

        /** The figures for these blocks and expenses; expenses that wait for their block are left out. */
        public static Figures of(List<ReportBlock> blocks, List<ReportCost> costs, VehicleCostMethod method,
                MileageRates rates) {
            List<Block> plain = new ArrayList<>(blocks.size());
            BigDecimal base = BigDecimal.ZERO;
            BigDecimal tips = BigDecimal.ZERO;
            int timed = 0;
            int early = 0;
            int clocked = 0;
            int routeBlocks = 0;
            int routeStops = 0;
            int routeMinutes = 0;
            int returnsCounted = 0;
            long returned = 0;
            long carried = 0;
            for (ReportBlock block : blocks) {
                plain.add(block.block());
                base = base.add(block.block().earnedBasePay());
                tips = tips.add(block.block().earnedTips());
                int minutes = block.clockedMinutes();
                clocked += minutes;
                Integer finishedEarly = block.finishedEarlyMinutes();
                if (finishedEarly != null) {
                    timed++;
                    early += finishedEarly;
                }
                if (block.minutesPerStop() != null) {
                    routeBlocks++;
                    routeStops += block.stops();
                    routeMinutes += minutes;
                }
                Integer returnsBase = block.returnsBase();
                if (returnsBase != null) {
                    returnsCounted++;
                    returned += block.returns();
                    carried += returnsBase;
                }
            }
            List<Cost> plainCosts = new ArrayList<>(costs.size());
            for (ReportCost cost : costs) {
                if (!cost.waits()) plainCosts.add(cost.cost());
            }
            NetEarnings net = NetEarnings.calculate(plain, plainCosts, method, rates);
            return new Figures(blocks.size(), money(base), money(tips), net.grossEarnings(), net.minutesWorked(),
                    net.grossHourlyRate(), net.miles(), net.mileageCost(), net.cashSpent(), net.totalDeductions(),
                    net.netEarnings(), net.netHourlyRate(), timed, clocked, hourly(net.grossEarnings(), clocked),
                    timed == 0 ? null : Math.round((float) early / timed), routeBlocks,
                    routeBlocks == 0 ? null : BigDecimal.valueOf(routeStops)
                            .divide(BigDecimal.valueOf(routeBlocks), 1, RoundingMode.HALF_UP),
                    routeBlocks == 0 ? null : BigDecimal.valueOf(routeMinutes)
                            .divide(BigDecimal.valueOf(routeStops), 1, RoundingMode.HALF_UP),
                    returnsCounted == 0 ? null : BigDecimal.valueOf(returned * 100)
                            .divide(BigDecimal.valueOf(carried), 1, RoundingMode.HALF_UP),
                    method == VehicleCostMethod.ACTUAL_EXPENSES ? 0 : net.blocksWithoutMiles());
        }
    }

    /**
     * Groups and totals the selected rows. {@code unlinkedLabel} is the consumer's own name for the unlinked bucket,
     * used only to order station buckets; it is never stored in the result.
     */
    public static EarningsReport calculate(List<ReportBlock> blocks, List<ReportCost> costs, GroupBy groupBy,
            VehicleCostMethod method, MileageRates rates, String unlinkedLabel) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (ReportBlock block : blocks) {
            group(groups, block.block().date(), block.block().station(), groupBy).blocks.add(block);
        }
        for (ReportCost cost : costs) {
            if (cost.waits()) continue;
            group(groups, cost.cost().date(), cost.countedStation(), groupBy).costs.add(cost);
        }
        List<Bucket> buckets = new ArrayList<>(groups.size());
        for (Group group : groups.values()) {
            buckets.add(new Bucket(group.key, group.kind, group.start, group.end, group.station,
                    Figures.of(group.blocks, group.costs, method, rates)));
        }
        if (groupBy == GroupBy.STATION) {
            String unlinked = unlinkedLabel == null ? "" : unlinkedLabel;
            Comparator<Bucket> byNet = (left, right) -> right.figures().netEarnings()
                    .compareTo(left.figures().netEarnings());
            buckets.sort(byNet.thenComparing(bucket -> bucket.kind() == Kind.UNLINKED ? unlinked : bucket.station(),
                    String.CASE_INSENSITIVE_ORDER));
        } else {
            buckets.sort(Comparator.comparing(Bucket::periodStart, Comparator.nullsLast(Comparator.naturalOrder())));
        }
        return new EarningsReport(Collections.unmodifiableList(buckets), Figures.of(blocks, costs, method, rates));
    }

    private static Group group(Map<String, Group> groups, LocalDate date, String station, GroupBy groupBy) {
        Group candidate = key(date, station, groupBy);
        Group existing = groups.get(candidate.key);
        if (existing != null) return existing;
        groups.put(candidate.key, candidate);
        return candidate;
    }

    private static Group key(LocalDate date, String station, GroupBy groupBy) {
        if (groupBy == GroupBy.STATION) {
            if (station == null || ReportSelection.normalize(station) == null) {
                return new Group(UNLINKED_KEY, Kind.UNLINKED, null, null, null);
            }
            String trimmed = station.trim();
            return new Group(trimmed.toLowerCase(Locale.ROOT), Kind.STATION, null, null, trimmed);
        }
        if (date == null) return new Group(UNKNOWN_DATE_KEY, Kind.UNKNOWN_DATE, null, null, null);
        switch (groupBy) {
            case WEEK: {
                LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                String key = String.format(Locale.ROOT, "%d-W%02d", date.get(IsoFields.WEEK_BASED_YEAR),
                        date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
                return new Group(key, Kind.PERIOD, start, start.plusDays(6), null);
            }
            case MONTH: {
                YearMonth month = YearMonth.from(date);
                return new Group(month.toString(), Kind.PERIOD, month.atDay(1), month.atEndOfMonth(), null);
            }
            default:
                return new Group(String.valueOf(date.getYear()), Kind.PERIOD, LocalDate.of(date.getYear(), 1, 1),
                        LocalDate.of(date.getYear(), 12, 31), null);
        }
    }

    private static final class Group {
        final String key;
        final Kind kind;
        final LocalDate start;
        final LocalDate end;
        final String station;
        final List<ReportBlock> blocks = new ArrayList<>();
        final List<ReportCost> costs = new ArrayList<>();

        Group(String key, Kind kind, LocalDate start, LocalDate end, String station) {
            this.key = key;
            this.kind = kind;
            this.start = start;
            this.end = end;
            this.station = station;
        }
    }
}
