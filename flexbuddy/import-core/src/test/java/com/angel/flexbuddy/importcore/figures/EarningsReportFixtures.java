package com.angel.flexbuddy.importcore.figures;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * One made-up account for the earnings report, as plain inputs, and the report's expected figures for each case in
 * {@code earnings-report.txt} beside this class. The website's EarningsReportGoldenTest seeds the same rows into its
 * database and checks its report against the same file, and the native app's parity test can reuse both.
 *
 * <p>Rows are in the website's query order (blocks by date and start, newest first; expenses by date and id, newest
 * first), because a station bucket takes its station from the first row and ties keep their first-seen order. Block
 * ids are 1 to 9 in the order the website saves them; block 9 is deleted, so it is only an expense's link.
 */
public final class EarningsReportFixtures {

    /** The website's English name for the unlinked bucket, which the station order uses. */
    public static final String UNLINKED_LABEL = "Unlinked expenses";

    private EarningsReportFixtures() {
    }

    /** Every live block of the account, scheduled and forfeited ones included. */
    public static List<ReportBlock> blocks() {
        return Arrays.asList(
                // Ran over, with zero stops recorded.
                block(8L, "BDL4", "2027-02-15", "09:00", "13:00", "72.33", "9.99", "20.3", BlockStatus.COMPLETED,
                        260, 0, null, null),
                // Overnight, and its packages are zero, so returns fall back to nothing rather than to stops.
                block(7L, "DLA8", "2027-01-04", "22:00", "02:00", "96.00", "20.25", "44.7", BlockStatus.COMPLETED,
                        205, 55, 0, 2),
                block(6L, "VEA7", "2027-01-03", "07:00", "11:00", "90.00", "0.00", null, BlockStatus.SCHEDULED,
                        null, null, null, null),
                block(5L, "dla8", "2026-12-31", "06:00", "09:00", "54.00", "0.00", "8.0", BlockStatus.FORFEITED,
                        null, null, null, null),
                block(4L, "DLA8", "2026-12-28", "18:00", "21:00", "54.00", "5.00", "12.0", BlockStatus.CANCELLED,
                        null, null, null, null),
                block(3L, "DLA8", "2026-06-30", "09:15", "12:45", "61.25", "4.75", "18.6", BlockStatus.COMPLETED,
                        170, 33, 70, 0),
                block(2L, " vea7 ", "2026-01-02", "10:00", "13:30", "63.00", "0.00", null, BlockStatus.COMPLETED,
                        null, 30, null, 1),
                // 2025-12-30 is in ISO week 2026-W01 but calendar year 2025.
                block(1L, "VEA7", "2025-12-30", "08:00", "12:00", "80.00", "12.50", "31.4", BlockStatus.COMPLETED,
                        215, 42, 110, 3));
    }

    /** Every live expense of the account, with the block each is linked to. */
    public static List<ReportCost> costs() {
        return Arrays.asList(
                linked("2027-02-15", CostCategory.FUEL, "41.10", 8L, BlockStatus.COMPLETED, false, "BDL4"),
                linked("2027-01-04", CostCategory.OTHER, "3.33", 7L, BlockStatus.COMPLETED, false, "DLA8"),
                // Waits for its scheduled block.
                linked("2027-01-03", CostCategory.PARKING, "4.00", 6L, BlockStatus.SCHEDULED, false, "VEA7"),
                // A forfeited block's expense counts under its station.
                linked("2026-12-31", CostCategory.TOLL, "2.50", 5L, BlockStatus.FORFEITED, false, "dla8"),
                ReportCost.unlinked(cost("2026-12-31", CostCategory.TOLL, "6.75")),
                // Its block is deleted, so it counts by its date, under no station.
                linked("2026-12-29", CostCategory.MAINTENANCE, "120.00", 9L, BlockStatus.COMPLETED, true, "DCY1"),
                linked("2026-01-02", CostCategory.FUEL, "25.40", 2L, BlockStatus.COMPLETED, false, " vea7 "),
                linked("2025-12-30", CostCategory.FUEL, "18.05", 1L, BlockStatus.COMPLETED, false, "VEA7"));
    }

    /** A legacy block with no date beside a dated one, for the unknown-date bucket. */
    public static List<ReportBlock> legacyBlocks() {
        return Arrays.asList(
                block(11L, "VEA7", "2026-02-10", "08:00", "09:00", "75.00", "0.00", null, BlockStatus.COMPLETED,
                        null, null, null, null),
                block(12L, "VEA7", null, "08:00", "09:00", "50.00", "0.00", null, BlockStatus.COMPLETED,
                        null, null, null, null));
    }

    public enum Settings {
        IRS(VehicleCostMethod.STANDARD_MILEAGE, MileageRates.irs()),
        CUSTOM(VehicleCostMethod.STANDARD_MILEAGE, MileageRates.fixed(new BigDecimal("0.655"))),
        ACTUAL(VehicleCostMethod.ACTUAL_EXPENSES, MileageRates.irs());

        public final VehicleCostMethod method;
        public final MileageRates rates;

        Settings(VehicleCostMethod method, MileageRates rates) {
            this.method = method;
            this.rates = rates;
        }
    }

    /** A report filter: the dates, station and statuses a driver can choose on the website's Reports page. */
    public enum Filter {
        NONE(null, null, null, null),
        STATION(null, null, "  vEa7 ", null),
        STATUS(null, null, null, EnumSet.of(BlockStatus.COMPLETED, BlockStatus.FORFEITED)),
        RANGE(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31), null, null);

        public final LocalDate from;
        public final LocalDate to;
        public final String station;
        public final Set<BlockStatus> statuses;

        Filter(LocalDate from, LocalDate to, String station, Set<BlockStatus> statuses) {
            this.from = from;
            this.to = to;
            this.station = station;
            this.statuses = statuses == null ? null : Collections.unmodifiableSet(statuses);
        }
    }

    /** One expected report: a line of {@code earnings-report.txt}. */
    public record Case(Settings settings, EarningsReport.GroupBy groupBy, Filter filter) {
        public String name() {
            return settings.name().toLowerCase() + "/" + groupBy.name().toLowerCase() + "/"
                    + filter.name().toLowerCase();
        }
    }

    /** Every filter with the IRS rate, and no filter with a custom rate and with actual expenses. */
    public static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        for (Settings settings : Settings.values()) {
            for (EarningsReport.GroupBy groupBy : Arrays.asList(EarningsReport.GroupBy.WEEK,
                    EarningsReport.GroupBy.MONTH, EarningsReport.GroupBy.YEAR, EarningsReport.GroupBy.STATION)) {
                for (Filter filter : Filter.values()) {
                    if (settings == Settings.IRS || filter == Filter.NONE) cases.add(new Case(settings, groupBy, filter));
                }
            }
        }
        return cases;
    }

    /** The case's report, from the rows {@link ReportSelection} picks. */
    public static EarningsReport report(Case report) {
        List<ReportBlock> blocks = new ArrayList<>();
        for (ReportBlock block : blocks()) {
            Filter filter = report.filter();
            if (ReportSelection.blockMatches(block, filter.from, filter.to, filter.station, filter.statuses)) {
                blocks.add(block);
            }
        }
        List<ReportCost> costs = new ArrayList<>();
        for (ReportCost cost : costs()) {
            Filter filter = report.filter();
            if (ReportSelection.costMatches(cost, filter.from, filter.to, filter.station)) costs.add(cost);
        }
        return EarningsReport.calculate(blocks, costs, report.groupBy(), report.settings().method,
                report.settings().rates, UNLINKED_LABEL);
    }

    /**
     * A report as one line: each bucket's key, period start and end and every figure, then the totals' figures that
     * the website's totals carry. Missing values print as {@code -}; amounts keep their scale.
     */
    public static String render(EarningsReport report) {
        StringBuilder line = new StringBuilder();
        for (EarningsReport.Bucket bucket : report.buckets()) {
            EarningsReport.Figures f = bucket.figures();
            line.append(join(bucket.key(), bucket.periodStart(), bucket.periodEnd(), f.shifts(), f.basePay(), f.tips(),
                    f.totalEarnings(), f.minutesWorked(), f.hourlyRate(), f.miles(), f.mileageCost(), f.expenses(),
                    f.deductions(), f.netEarnings(), f.netHourlyRate(), f.timedShifts(), f.clockedMinutes(),
                    f.clockedHourlyRate(), f.averageFinishedEarlyMinutes(), f.shiftsWithRouteData(),
                    f.averageStops(), f.averageMinutesPerStop(), f.returnsRate(), f.netMissingMiles()))
                    .append(" | ");
        }
        EarningsReport.Figures t = report.totals();
        line.append("totals ").append(join(t.shifts(), t.basePay(), t.tips(), t.totalEarnings(), t.minutesWorked(),
                t.hourlyRate(), t.miles(), t.mileageCost(), t.expenses(), t.deductions(), t.netEarnings(),
                t.netHourlyRate()));
        return line.toString();
    }

    public static String join(Object... values) {
        StringBuilder joined = new StringBuilder();
        for (Object value : values) {
            if (joined.length() > 0) joined.append(',');
            joined.append(value == null ? "-" : value instanceof BigDecimal amount ? amount.toPlainString() : value);
        }
        return joined.toString();
    }

    /** The expected lines, "name rendered-report", as stored. */
    public static String expected() throws IOException {
        try (InputStream input = EarningsReportFixtures.class.getResourceAsStream("earnings-report.txt")) {
            if (input == null) throw new IOException("earnings-report.txt is missing");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
    }

    private static ReportBlock block(Long id, String station, String date, String start, String end, String base,
            String tips, String miles, BlockStatus status, Integer actualMinutes, Integer stops, Integer packages,
            Integer returns) {
        Block block = new Block(status, station, date == null ? null : LocalDate.parse(date), LocalTime.parse(start),
                LocalTime.parse(end), new BigDecimal(base), new BigDecimal(tips),
                miles == null ? null : new BigDecimal(miles));
        return new ReportBlock(id, block, actualMinutes, stops, packages, returns);
    }

    private static ReportCost linked(String date, CostCategory category, String amount, Long blockId,
            BlockStatus status, boolean deleted, String station) {
        return new ReportCost(cost(date, category, amount), blockId, status, deleted, station);
    }

    private static Cost cost(String date, CostCategory category, String amount) {
        return new Cost(category, new BigDecimal(amount), LocalDate.parse(date));
    }
}
