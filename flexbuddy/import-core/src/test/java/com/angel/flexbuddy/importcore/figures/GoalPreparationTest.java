package com.angel.flexbuddy.importcore.figures;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class GoalPreparationTest {
    private static final LocalDate DATE = LocalDate.of(2026, 6, 30);
    private static final Block COMPLETED = block(BlockStatus.COMPLETED, "100", "20", "10", 241);
    private static final Block CANCELLED = block(BlockStatus.CANCELLED, "20", "90", "5", 120);
    private static final Block SCHEDULED = block(BlockStatus.SCHEDULED, "90", "99", "999", 240);

    @Test void historyUsesReportMoneyButOnlyCompletedMinutesAndCount() {
        var history = history(VehicleCostMethod.STANDARD_MILEAGE, MileageRates.irs());
        var gross = GoalPreparation.fromHistory(history, List.of(COMPLETED, CANCELLED), false);
        assertEquals(new BigDecimal("140.00"), gross.averageBlockPay());
        assertEquals(241, gross.averageBlockMinutes());
        assertEquals(BigDecimal.ONE, gross.keptShare());
        assertEquals(new BigDecimal("90"), gross.plannedPay(List.of(SCHEDULED, COMPLETED)));
        assertEquals(new BigDecimal("140.00"), gross.earned(history));
        var net = GoalPreparation.fromHistory(history, List.of(COMPLETED), true);
        assertEquals(new BigDecimal("124.12"), net.averageBlockPay());
        assertEquals(new BigDecimal("0.8866"), net.keptShare());
        assertEquals(new BigDecimal("79.7940"), net.plannedPay(List.of(SCHEDULED)));
        assertEquals(new BigDecimal("124.12"), net.earned(history));
    }

    @Test void actualAndCustomZeroPreserveTheirDistinctNetEstimates() {
        var actual = GoalPreparation.fromHistory(history(VehicleCostMethod.ACTUAL_EXPENSES, MileageRates.irs()), List.of(COMPLETED), true);
        assertEquals(new BigDecimal("120.00"), actual.averageBlockPay());
        assertEquals(new BigDecimal("0.8571"), actual.keptShare());
        assertEquals(new BigDecimal("77.1390"), actual.plannedPay(List.of(SCHEDULED)));
        var zero = GoalPreparation.fromHistory(history(VehicleCostMethod.STANDARD_MILEAGE, MileageRates.fixed(BigDecimal.ZERO)), List.of(COMPLETED), true);
        assertEquals(new BigDecimal("135.00"), zero.averageBlockPay());
        assertEquals(new BigDecimal("0.9643"), zero.keptShare());
        assertEquals(new BigDecimal("86.7870"), zero.plannedPay(List.of(SCHEDULED)));
        var custom = GoalPreparation.fromHistory(history(VehicleCostMethod.STANDARD_MILEAGE, MileageRates.fixed(new BigDecimal("0.650"))), List.of(COMPLETED), true);
        assertEquals(new BigDecimal("125.25"), custom.averageBlockPay());
        assertEquals(new BigDecimal("0.8946"), custom.keptShare());
        assertEquals(new BigDecimal("80.5140"), custom.plannedPay(List.of(SCHEDULED)));
    }

    @Test void missingCompletedSampleKeepsNullAveragesButUsesHistoryShare() {
        var cancelled = NetEarnings.calculate(List.of(CANCELLED), List.of(), VehicleCostMethod.STANDARD_MILEAGE, MileageRates.fixed(BigDecimal.ZERO));
        var prepared = GoalPreparation.fromHistory(cancelled, List.of(CANCELLED, SCHEDULED), true);
        assertNull(prepared.averageBlockPay());
        assertNull(prepared.averageBlockMinutes());
        assertEquals(new BigDecimal("1.0000"), prepared.keptShare());
        assertEquals(new BigDecimal("90.0000"), prepared.plannedPay(List.of(SCHEDULED)));
    }

    @Test void zeroGrossDefaultsToOneAndNegativeOrAboveOneSharesAreNotClamped() {
        Block free = block(BlockStatus.COMPLETED, "0", "0", "0", 60);
        var zero = NetEarnings.calculate(List.of(free), List.of(cost("10")), VehicleCostMethod.ACTUAL_EXPENSES, null);
        var prepared = GoalPreparation.fromHistory(zero, List.of(free), true);
        assertEquals(BigDecimal.ONE, prepared.keptShare());
        assertEquals(new BigDecimal("-10.00"), prepared.averageBlockPay());
        assertEquals(new BigDecimal("90"), prepared.plannedPay(List.of(SCHEDULED)));
        Block paid = block(BlockStatus.COMPLETED, "100", "0", "0", 60);
        var negative = NetEarnings.calculate(List.of(paid), List.of(cost("110")), VehicleCostMethod.ACTUAL_EXPENSES, null);
        prepared = GoalPreparation.fromHistory(negative, List.of(paid), true);
        assertEquals(new BigDecimal("-0.1000"), prepared.keptShare());
        assertEquals(new BigDecimal("-9.0000"), prepared.plannedPay(List.of(SCHEDULED)));
        // Synthetic shared numeric input proves no new cap, independently of server expense-entry validation.
        var above = NetEarnings.calculate(List.of(paid), List.of(cost("-20")), VehicleCostMethod.ACTUAL_EXPENSES, null);
        prepared = GoalPreparation.fromHistory(above, List.of(paid), true);
        assertEquals(new BigDecimal("1.2000"), prepared.keptShare());
        assertEquals(new BigDecimal("108.0000"), prepared.plannedPay(List.of(SCHEDULED)));
    }

    @Test void plannedPayStaysUnroundedUntilGoalProgressCalculatesRemaining() {
        Block paid = block(BlockStatus.COMPLETED, "100", "0", "0", 60);
        var history = NetEarnings.calculate(List.of(paid), List.of(cost("50")), VehicleCostMethod.ACTUAL_EXPENSES, null);
        var prepared = GoalPreparation.fromHistory(history, List.of(paid), true);
        BigDecimal planned = prepared.plannedPay(List.of(block(BlockStatus.SCHEDULED, "0.01", "9", "0", 60)));
        assertEquals(new BigDecimal("0.005000"), planned);
        var progress = GoalProgress.of(BigDecimal.ONE, BigDecimal.ZERO, planned, prepared.averageBlockPay(),
                prepared.averageBlockMinutes(), DATE, DATE.plusDays(6), DATE);
        assertEquals(new BigDecimal("0.01"), progress.planned());
        assertEquals(new BigDecimal("1.00"), progress.remaining()); // Prematurely rounding planned yields .99.
        assertEquals(1, progress.blocksToGo());
    }

    @Test void averagesUseFloatMinuteRoundingAndDoNotRetainMutableInputs() {
        var blocks = new ArrayList<>(List.of(block(BlockStatus.COMPLETED, "1.00", "0", "0", 1),
                block(BlockStatus.COMPLETED, "2.01", "0", "0", 2)));
        var copy = List.copyOf(blocks);
        var history = NetEarnings.calculate(blocks, List.of(), VehicleCostMethod.ACTUAL_EXPENSES, null);
        var prepared = GoalPreparation.fromHistory(history, blocks, false);
        assertEquals(new BigDecimal("1.51"), prepared.averageBlockPay());
        assertEquals(2, prepared.averageBlockMinutes());
        assertEquals(copy, blocks);
        blocks.clear();
        assertEquals(new BigDecimal("1.51"), prepared.averageBlockPay());
        assertEquals(new BigDecimal("0"), prepared.plannedPay(List.of()));
    }

    @Test void historyBoundaryIsInclusiveAcrossLeapAndYearChanges() {
        assertEquals(LocalDate.of(2026, 6, 15), GoalPreparation.historyStart(LocalDate.of(2026, 9, 12)));
        assertEquals(LocalDate.of(2023, 12, 2), GoalPreparation.historyStart(LocalDate.of(2024, 2, 29)));
        assertEquals(LocalDate.of(2026, 10, 4), GoalPreparation.historyStart(LocalDate.of(2027, 1, 1)));
    }

    private static NetEarnings history(VehicleCostMethod method, MileageRates rates) {
        return NetEarnings.calculate(List.of(COMPLETED, CANCELLED), List.of(cost("5"), new Cost(CostCategory.FUEL, new BigDecimal("15"), DATE)), method, rates);
    }
    private static Cost cost(String amount) { return new Cost(CostCategory.OTHER, new BigDecimal(amount), DATE); }
    private static Block block(BlockStatus status, String base, String tips, String miles, int minutes) {
        return new Block(status, "ZZT9", DATE, LocalTime.of(8, 0), LocalTime.of(8, 0).plusMinutes(minutes),
                new BigDecimal(base), new BigDecimal(tips), new BigDecimal(miles));
    }
}
