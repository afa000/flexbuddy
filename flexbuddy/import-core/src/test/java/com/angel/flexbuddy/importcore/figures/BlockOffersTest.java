package com.angel.flexbuddy.importcore.figures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.importcore.figures.BlockOffers.Basis;
import com.angel.flexbuddy.importcore.figures.BlockOffers.Evaluation;
import com.angel.flexbuddy.importcore.figures.BlockOffers.Offer;
import com.angel.flexbuddy.importcore.figures.BlockOffers.Sample;
import com.angel.flexbuddy.importcore.figures.BlockOffers.Verdict;

class BlockOffersTest {
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);
    private static final MileageRates SEVENTY_CENTS = MileageRates.fixed(new BigDecimal("0.70"));

    @Test
    void threeRecentStationBlocksAreEnoughForTheNinetyDayWindow() {
        List<Block> blocks = List.of(
                worked("VEA7", TODAY.minusDays(1), 240, "84.00"), worked("DSE4", TODAY.minusDays(2), 240, "60.00"),
                worked("vea7", TODAY.minusDays(3), 240, "84.00"), worked("VEA7", TODAY.minusDays(89), 240, "84.00"));

        Sample<Block> sample = choose(blocks, " vea7 ");

        assertEquals(Basis.STATION_90_DAYS, sample.basis());
        assertEquals(LocalDate.of(2026, 6, 29), sample.from());
        assertEquals(List.of(blocks.get(0), blocks.get(2), blocks.get(3)), sample.blocks(), "newest first, as given");
    }

    @Test
    void anOlderBlockMovesTheSampleToTheStationsWholeHistory() {
        List<Block> blocks = List.of(worked("VEA7", TODAY.minusDays(1), 240, "84.00"),
                worked("VEA7", TODAY.minusDays(2), 240, "84.00"), worked("VEA7", TODAY.minusDays(90), 240, "84.00"));

        Sample<Block> sample = choose(blocks, "VEA7");

        assertEquals(Basis.STATION_ALL_TIME, sample.basis());
        assertNull(sample.from());
        assertEquals(3, sample.blocks().size());
    }

    @Test
    void withoutEnoughStationHistoryTheWholeAccountIsUsed() {
        List<Block> blocks = List.of(worked("VEA7", TODAY.minusDays(1), 240, "84.00"),
                worked("DSE4", TODAY.minusDays(2), 240, "60.00"), worked("VEA7", TODAY.minusDays(3), 240, "84.00"));

        Sample<Block> sample = choose(blocks, "VEA7");

        assertEquals(Basis.ACCOUNT, sample.basis());
        assertEquals(blocks, sample.blocks());
    }

    @Test
    void onlyCompletedBlocksWithTimeUpToTodayCount() {
        List<Block> blocks = new ArrayList<>();
        blocks.add(new Block(BlockStatus.SCHEDULED, "VEA7", TODAY.minusDays(1), LocalTime.NOON, LocalTime.of(16, 0),
                new BigDecimal("84.00"), BigDecimal.ZERO, null));
        blocks.add(new Block(BlockStatus.CANCELLED, "VEA7", TODAY.minusDays(1), LocalTime.NOON, LocalTime.of(16, 0),
                new BigDecimal("18.00"), BigDecimal.ZERO, null));
        blocks.add(worked("VEA7", TODAY.minusDays(1), 0, "84.00"));
        blocks.add(worked("VEA7", TODAY.plusDays(1), 240, "84.00"));
        Block counted = new Block(null, "VEA7", TODAY, LocalTime.NOON, LocalTime.of(16, 0), new BigDecimal("84.00"),
                BigDecimal.ZERO, null);
        blocks.add(counted);

        Sample<Block> sample = choose(blocks, "VEA7");

        assertEquals(Basis.ACCOUNT, sample.basis());
        assertEquals(List.of(counted), sample.blocks(), "a missing status is completed; today counts");
    }

    @Test
    void theSampleKeepsTheConsumersOwnObjects() {
        List<String> ids = List.of("a", "b", "c");
        Function<String, Block> view = id -> worked("VEA7", TODAY.minusDays(ids.indexOf(id) + 1), 240, "84.00");

        Sample<String> sample = BlockOffers.chooseSample(ids, view, "VEA7", TODAY);

        assertEquals(List.of("a", "b", "c"), sample.blocks());
    }

    @Test
    void theOfferIsEstimatedWithTheStationsRatesAndComparedWithAUsualBlock() {
        List<Block> blocks = new ArrayList<>();
        for (int day = 1; day <= 3; day++) {
            blocks.add(block(BlockStatus.COMPLETED, "VEA7", TODAY.minusDays(day), 240, "84.00", "6.00", "22.0"));
        }
        List<Cost> linked = List.of(new Cost(CostCategory.TOLL, new BigDecimal("4.00"), TODAY.minusDays(1)),
                new Cost(CostCategory.FUEL, new BigDecimal("10.00"), TODAY.minusDays(1)));

        Evaluation result = BlockOffers.evaluate(offer("VEA7", "4", "90.00", null), choose(blocks, "VEA7"), linked,
                List.of(), List.of(), VehicleCostMethod.STANDARD_MILEAGE, SEVENTY_CENTS, TODAY);

        assertEquals(Basis.STATION_90_DAYS, result.basis());
        assertEquals(3, result.sampleSize());
        assertEquals("VEA7", result.station());
        assertEquals(new BigDecimal("22.50"), result.offeredHourly());
        assertEquals(new BigDecimal("24.00"), result.grossHourly());
        assertEquals(new BigDecimal("6.00"), result.estimatedTips());
        assertEquals(new BigDecimal("22.0"), result.estimatedMiles());
        // 22 miles at $0.70; the $4 toll over 12 hours is $1.33 for 4; linked fuel is left to the vehicle cost.
        assertEquals(new BigDecimal("15.40"), result.estimatedVehicleCost());
        assertEquals(new BigDecimal("1.33"), result.estimatedOtherExpenses());
        assertEquals(new BigDecimal("79.27"), result.estimatedNet());
        assertEquals(new BigDecimal("19.82"), result.estimatedNetHourly());
        assertEquals(new BigDecimal("18.32"), result.usualNetHourly());
        assertEquals(new BigDecimal("22.50"), result.usualGrossHourly());
        assertEquals(new BigDecimal("8.2"), result.differencePercent());
        assertEquals(Verdict.ABOVE_USUAL, result.verdict());
        assertEquals(new BigDecimal("21.00"), result.usualBaseHourly());
        assertEquals(new BigDecimal("6.00"), result.surgePay());
    }

    @Test
    void withNoHistoryThereIsNothingToCompare() {
        Evaluation result = BlockOffers.evaluate(offer(" zzz1 ", "2", "40.00", null), choose(List.of(), "zzz1"),
                List.of(), List.of(), List.of(), VehicleCostMethod.STANDARD_MILEAGE, MileageRates.irs(), TODAY);

        assertEquals(Basis.ACCOUNT, result.basis());
        assertEquals(0, result.sampleSize());
        assertEquals("zzz1", result.station());
        assertEquals(new BigDecimal("0.00"), result.estimatedTips());
        assertNull(result.estimatedMiles());
        assertEquals(new BigDecimal("40.00"), result.estimatedNet());
        assertEquals(new BigDecimal("20.00"), result.estimatedNetHourly());
        assertNull(result.usualNetHourly());
        assertNull(result.usualGrossHourly());
        assertNull(result.verdict());
        assertNull(result.differencePercent());
        assertNull(result.usualBaseHourly());
        assertNull(result.surgePay());
    }

    @Test
    void theVerdictUsesTheRoundedDifferenceAroundFivePercent() {
        assertEquals(Verdict.ABOUT_USUAL, verdictFor("21.00"), "+5.0% is about usual");
        assertEquals(Verdict.ABOVE_USUAL, verdictFor("21.02"), "+5.1%");
        assertEquals(Verdict.ABOUT_USUAL, verdictFor("19.00"), "-5.0%");
        assertEquals(Verdict.BELOW_USUAL, verdictFor("18.99"), "-5.05% rounds to -5.1%");
    }

    @Test
    void theUsualBaseRateIsTheMostCommonAndATieGoesToTheHigherRate() {
        assertEquals(new BigDecimal("20.00"), BlockOffers.usualBaseHourly(List.of(
                worked("A", TODAY, 60, "18.00"), worked("A", TODAY, 60, "20.00"),
                worked("A", TODAY, 120, "36.00"), worked("A", TODAY, 120, "40.00"))));
        assertEquals(new BigDecimal("18.00"), BlockOffers.usualBaseHourly(List.of(
                worked("A", TODAY, 60, "18.00"), worked("A", TODAY, 60, "18.00"),
                worked("A", TODAY, 60, "18.00"), worked("A", TODAY, 60, "27.00"))));
        assertNull(BlockOffers.usualBaseHourly(List.of()));
        assertNull(BlockOffers.usualBaseHourly(List.of(worked("A", TODAY, 0, "18.00"))));
    }

    @Test
    void actualExpensesSpreadFuelAndMaintenanceOverTheWindowsMiles() {
        Evaluation result = actualExpenses("20.0");

        // $60 of fuel in the window over 80 miles driven is $0.75 a mile; the July maintenance is before the window.
        assertEquals(new BigDecimal("20.0"), result.estimatedMiles());
        assertEquals(new BigDecimal("15.00"), result.estimatedVehicleCost());
        assertEquals(new BigDecimal("69.00"), result.estimatedNet());
    }

    @Test
    void withoutMilesActualExpensesAreSpreadOverTheHoursWorked() {
        Evaluation result = actualExpenses(null);

        assertNull(result.estimatedMiles());
        // $60 over 12 hours worked is $20 for a 4-hour block.
        assertEquals(new BigDecimal("20.00"), result.estimatedVehicleCost());
        assertEquals(new BigDecimal("64.00"), result.estimatedNet());
    }

    private static Evaluation actualExpenses(String miles) {
        List<Block> sampleBlocks = new ArrayList<>();
        for (int day = 1; day <= 3; day++) {
            sampleBlocks.add(block(BlockStatus.COMPLETED, "VEA7", TODAY.minusDays(day), 240, "84.00", "0", miles));
        }
        List<Block> window = new ArrayList<>(sampleBlocks);
        window.add(block(BlockStatus.CANCELLED, "VEA7", TODAY.minusDays(4), 240, "18.00", "0", miles));
        List<Cost> costs = List.of(new Cost(CostCategory.FUEL, new BigDecimal("60.00"), TODAY.minusDays(10)),
                new Cost(CostCategory.MAINTENANCE, new BigDecimal("30.00"), LocalDate.of(2026, 6, 1)),
                new Cost(CostCategory.TOLL, new BigDecimal("5.00"), TODAY.minusDays(10)));
        Sample<Block> sample = choose(sampleBlocks, "VEA7");
        assertEquals(Basis.STATION_90_DAYS, sample.basis());
        return BlockOffers.evaluate(offer("VEA7", "4", "84.00", "0"), sample, List.of(), costs, window,
                VehicleCostMethod.ACTUAL_EXPENSES, MileageRates.irs(), TODAY);
    }

    private static Verdict verdictFor(String pay) {
        List<Block> blocks = List.of(worked("A", TODAY.minusDays(1), 60, "20.00"),
                worked("A", TODAY.minusDays(2), 60, "20.00"), worked("A", TODAY.minusDays(3), 60, "20.00"));
        return BlockOffers.evaluate(offer("A", "1", pay, "0"), choose(blocks, "A"), List.of(), List.of(), List.of(),
                VehicleCostMethod.STANDARD_MILEAGE, MileageRates.irs(), TODAY).verdict();
    }

    private static Sample<Block> choose(List<Block> blocks, String station) {
        return BlockOffers.chooseSample(blocks, Function.identity(), station, TODAY);
    }

    private static Offer offer(String station, String hours, String pay, String tips) {
        return new Offer(station, new BigDecimal(hours), new BigDecimal(pay), tips == null ? null : new BigDecimal(tips));
    }

    private static Block worked(String station, LocalDate date, int minutes, String base) {
        return block(BlockStatus.COMPLETED, station, date, minutes, base, "0", null);
    }

    private static Block block(BlockStatus status, String station, LocalDate date, int minutes, String base,
            String tips, String miles) {
        return new Block(status, station, date, LocalTime.of(8, 0), LocalTime.of(8, 0).plusMinutes(minutes),
                new BigDecimal(base), new BigDecimal(tips), miles == null ? null : new BigDecimal(miles));
    }
}
