package com.angel.flexbuddy.importcore.figures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The earnings report from plain inputs, against the website's figures for the shared fixtures. */
class EarningsReportTest {

    private static final Path EXPECTED = Path.of("src", "test", "resources", "com", "angel", "flexbuddy",
            "importcore", "figures", "earnings-report.txt");

    @Test
    void everyCaseMatchesTheWebsitesFigures() throws IOException {
        StringBuilder text = new StringBuilder();
        for (EarningsReportFixtures.Case report : EarningsReportFixtures.cases()) {
            text.append(report.name()).append(' ').append(EarningsReportFixtures.render(
                    EarningsReportFixtures.report(report))).append('\n');
        }
        if ("1".equals(System.getenv("FLEXBUDDY_WRITE_GOLDEN"))) {
            Files.writeString(EXPECTED, text, StandardCharsets.UTF_8);
        }
        assertEquals(EarningsReportFixtures.expected(), text.toString());
    }

    @Test
    void weeksUseTheIsoWeekYearAcrossTheYearChange() {
        EarningsReport report = calculate(EarningsReport.GroupBy.WEEK, Arrays.asList(
                block("VEA7", "2027-01-04", BlockStatus.COMPLETED), block("VEA7", "2027-01-01", BlockStatus.COMPLETED),
                block("VEA7", "2026-12-27", BlockStatus.COMPLETED), block("VEA7", "2025-12-30", BlockStatus.COMPLETED)));

        assertEquals(Arrays.asList("2026-W01", "2026-W52", "2026-W53", "2027-W01"), keys(report));
        EarningsReport.Bucket week53 = report.buckets().get(2);
        assertEquals(EarningsReport.Kind.PERIOD, week53.kind());
        assertEquals(LocalDate.of(2026, 12, 28), week53.periodStart());
        assertEquals(LocalDate.of(2027, 1, 3), week53.periodEnd());
        assertNull(week53.station());
    }

    @Test
    void aRowWithNoDateGoesLastInTheUnknownBucket() {
        for (EarningsReport.GroupBy groupBy : Arrays.asList(EarningsReport.GroupBy.WEEK,
                EarningsReport.GroupBy.MONTH, EarningsReport.GroupBy.YEAR)) {
            EarningsReport report = EarningsReport.calculate(EarningsReportFixtures.legacyBlocks(),
                    new ArrayList<>(), groupBy, VehicleCostMethod.STANDARD_MILEAGE, MileageRates.irs(), "Unlinked");

            assertEquals(2, report.buckets().size());
            EarningsReport.Bucket unknown = report.buckets().get(1);
            assertEquals(EarningsReport.UNKNOWN_DATE_KEY, unknown.key());
            assertEquals(EarningsReport.Kind.UNKNOWN_DATE, unknown.kind());
            assertNull(unknown.periodStart());
            assertEquals(new BigDecimal("50.00"), unknown.figures().totalEarnings());
        }
    }

    @Test
    void stationsGroupOnTheTrimmedStationAndKeepTheFirstRowsSpelling() {
        EarningsReport report = calculate(EarningsReport.GroupBy.STATION, Arrays.asList(
                block(" vea7 ", "2026-09-02", BlockStatus.COMPLETED), block("VEA7", "2026-09-01", BlockStatus.COMPLETED),
                block("  ", "2026-09-03", BlockStatus.COMPLETED)));

        assertEquals(Arrays.asList("vea7", EarningsReport.UNLINKED_KEY), keys(report));
        assertEquals("vea7", report.buckets().get(0).station());
        assertEquals(EarningsReport.Kind.STATION, report.buckets().get(0).kind());
        assertEquals(2, report.buckets().get(0).figures().shifts());
        // A block with a blank station shares the unlinked bucket, as on the website.
        assertEquals(EarningsReport.Kind.UNLINKED, report.buckets().get(1).kind());
        assertNull(report.buckets().get(1).station());
    }

    @Test
    void stationsWithTheSameNetAreOrderedByTheConsumersLabel() {
        List<ReportCost> costs = Arrays.asList(ReportCost.unlinked(
                new Cost(CostCategory.TOLL, new BigDecimal("50.00"), LocalDate.of(2026, 9, 1))));
        // MMM1 nets 50.00; the unlinked bucket (a blank-station block and an unlinked toll) nets 100.00 - 50.00.
        List<ReportBlock> tied = Arrays.asList(
                new ReportBlock(1L, new Block(BlockStatus.CANCELLED, "MMM1", LocalDate.of(2026, 9, 1),
                        LocalTime.of(8, 0), LocalTime.of(9, 0), new BigDecimal("50.00"), BigDecimal.ZERO, null),
                        null, null, null, null),
                new ReportBlock(2L, new Block(BlockStatus.CANCELLED, "", LocalDate.of(2026, 9, 1),
                        LocalTime.of(8, 0), LocalTime.of(9, 0), new BigDecimal("100.00"), BigDecimal.ZERO, null),
                        null, null, null, null));

        EarningsReport english = EarningsReport.calculate(tied, costs, EarningsReport.GroupBy.STATION,
                VehicleCostMethod.ACTUAL_EXPENSES, MileageRates.irs(), "Unlinked expenses");
        EarningsReport spanish = EarningsReport.calculate(tied, costs, EarningsReport.GroupBy.STATION,
                VehicleCostMethod.ACTUAL_EXPENSES, MileageRates.irs(), "Gastos sin vincular");

        assertEquals(new BigDecimal("50.00"), english.buckets().get(0).figures().netEarnings());
        assertEquals(new BigDecimal("50.00"), english.buckets().get(1).figures().netEarnings());
        assertEquals(Arrays.asList("mmm1", EarningsReport.UNLINKED_KEY), keys(english));
        assertEquals(Arrays.asList(EarningsReport.UNLINKED_KEY, "mmm1"), keys(spanish));
    }

    @Test
    void expensesThatWaitAreLeftOutAndOnesOnADeletedBlockCountUnlinked() {
        Cost cost = new Cost(CostCategory.TOLL, new BigDecimal("5.00"), LocalDate.of(2026, 9, 6));
        ReportCost waiting = new ReportCost(cost, 1L, BlockStatus.SCHEDULED, false, "VEA7");
        ReportCost deletedScheduled = new ReportCost(cost, 2L, BlockStatus.SCHEDULED, true, "DCY1");
        ReportCost onForfeit = new ReportCost(cost, 3L, BlockStatus.FORFEITED, false, "DLA8");
        ReportCost oldRow = new ReportCost(cost, 4L, null, false, "BDL4");

        assertTrue(waiting.waits());
        assertFalse(deletedScheduled.waits());
        assertNull(deletedScheduled.countedStation());
        assertFalse(onForfeit.waits());
        assertEquals("DLA8", onForfeit.countedStation());
        assertFalse(oldRow.waits());
        assertFalse(ReportCost.unlinked(cost).waits());
        assertNull(ReportCost.unlinked(cost).countedStation());

        EarningsReport report = EarningsReport.calculate(new ArrayList<>(), Arrays.asList(waiting, deletedScheduled),
                EarningsReport.GroupBy.STATION, VehicleCostMethod.STANDARD_MILEAGE, MileageRates.irs(), "Unlinked");
        assertEquals(Arrays.asList(EarningsReport.UNLINKED_KEY), keys(report));
        assertEquals(new BigDecimal("5.00"), report.totals().expenses());
    }

    @Test
    void routeRulesCountOnlyWorkedBlocks() {
        LocalTime eight = LocalTime.of(8, 0);
        LocalTime noon = LocalTime.of(12, 0);
        assertEquals(Integer.valueOf(205), RouteRules.actualMinutes(LocalTime.of(22, 10), LocalTime.of(1, 35)));
        assertNull(RouteRules.actualMinutes(eight, null));
        assertEquals(215, RouteRules.clockedMinutes(BlockStatus.COMPLETED, eight, noon, 215));
        assertEquals(240, RouteRules.clockedMinutes(null, eight, noon, null));
        assertEquals(0, RouteRules.clockedMinutes(BlockStatus.CANCELLED, eight, noon, 215));
        assertEquals(Integer.valueOf(-20), RouteRules.finishedEarlyMinutes(BlockStatus.COMPLETED, eight, noon, 260));
        assertNull(RouteRules.finishedEarlyMinutes(BlockStatus.FORFEITED, eight, noon, 200));
        assertEquals(new BigDecimal("5.1"), RouteRules.minutesPerStop(BlockStatus.COMPLETED, eight, noon, 215, 42));
        assertNull(RouteRules.minutesPerStop(BlockStatus.COMPLETED, eight, noon, 215, 0));
        assertNull(RouteRules.minutesPerStop(BlockStatus.SCHEDULED, eight, noon, null, 42));
        assertEquals(Integer.valueOf(110), RouteRules.returnsBase(42, 110, 3));
        assertEquals(Integer.valueOf(30), RouteRules.returnsBase(30, null, 1));
        assertNull(RouteRules.returnsBase(55, 0, 2));
        assertNull(RouteRules.returnsBase(42, 110, null));
        assertEquals(new BigDecimal("2.7"), RouteRules.returnsRate(42, 110, 3));
        assertNull(RouteRules.returnsRate(null, null, 2));
    }

    @Test
    void selectionReadsTheStationFilterAsTheWebsiteDoes() {
        ReportBlock padded = block(" vea7 ", "2026-01-02", BlockStatus.COMPLETED);
        ReportBlock plain = block("VEA7", "2026-01-02", BlockStatus.COMPLETED);

        assertTrue(ReportSelection.blockMatches(plain, null, null, "  vEa7 ", null));
        // The stored station is not trimmed, so a padded one only matches when no station is chosen.
        assertFalse(ReportSelection.blockMatches(padded, null, null, "vea7", null));
        assertTrue(ReportSelection.blockMatches(padded, null, null, " \t ", null));
        assertNull(ReportSelection.normalize(" "));
    }

    @Test
    void selectionDefaultsToTheStatusesThatCarryEarningsAndNeverPicksAnUndatedRow() {
        LocalDate day = LocalDate.of(2026, 9, 6);
        assertTrue(ReportSelection.blockMatches(block("VEA7", "2026-09-06", BlockStatus.CANCELLED), day, day, null,
                null));
        assertFalse(ReportSelection.blockMatches(block("VEA7", "2026-09-06", BlockStatus.FORFEITED), null, null, null,
                EnumSet.noneOf(BlockStatus.class)));
        assertTrue(ReportSelection.blockMatches(block("VEA7", "2026-09-06", BlockStatus.FORFEITED), null, null, null,
                EnumSet.of(BlockStatus.FORFEITED)));
        assertFalse(ReportSelection.blockMatches(block("VEA7", "2026-09-07", BlockStatus.COMPLETED), day, day, null,
                null));
        assertFalse(ReportSelection.blockMatches(EarningsReportFixtures.legacyBlocks().get(1), null, null, null,
                null));
    }

    @Test
    void anExpenseMatchesAStationOnlyThroughALiveLinkedBlock() {
        Cost cost = new Cost(CostCategory.FUEL, new BigDecimal("10.00"), LocalDate.of(2026, 9, 6));
        assertTrue(ReportSelection.costMatches(new ReportCost(cost, 1L, BlockStatus.COMPLETED, false, "VEA7"),
                null, null, "vea7"));
        assertFalse(ReportSelection.costMatches(new ReportCost(cost, 1L, BlockStatus.COMPLETED, true, "VEA7"),
                null, null, "vea7"));
        assertFalse(ReportSelection.costMatches(ReportCost.unlinked(cost), null, null, "vea7"));
        assertTrue(ReportSelection.costMatches(ReportCost.unlinked(cost), null, null, null));
        assertFalse(ReportSelection.costMatches(new ReportCost(cost, 1L, BlockStatus.SCHEDULED, false, "VEA7"),
                null, null, null));
    }

    private static EarningsReport calculate(EarningsReport.GroupBy groupBy, List<ReportBlock> blocks) {
        return EarningsReport.calculate(blocks, new ArrayList<>(), groupBy, VehicleCostMethod.STANDARD_MILEAGE,
                MileageRates.irs(), "Unlinked expenses");
    }

    private static ReportBlock block(String station, String date, BlockStatus status) {
        return new ReportBlock(1L, new Block(status, station, LocalDate.parse(date), LocalTime.of(8, 0),
                LocalTime.of(9, 0), new BigDecimal("100.00"), BigDecimal.ZERO, null), null, null, null, null);
    }

    private static List<String> keys(EarningsReport report) {
        List<String> keys = new ArrayList<>();
        for (EarningsReport.Bucket bucket : report.buckets()) keys.add(bucket.key());
        return keys;
    }
}
