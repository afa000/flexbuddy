package com.angel.flexbuddy.importcore.figures;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

class NetEarningsTest {
    /** One block of each status, a block across midnight, one without miles, and July 1, 2026's IRS rate change. */
    private static final List<Block> BLOCKS = List.of(
            block(BlockStatus.COMPLETED, "2026-06-30", "10:00", "14:00", "72.00", "8.00", "40.0"),
            block(BlockStatus.COMPLETED, "2026-07-01", "22:00", "01:30", "70.00", "0", "35.0"),
            block(BlockStatus.CANCELLED, "2026-07-02", "10:00", "13:30", "18.00", "0", "20.0"),
            block(BlockStatus.SCHEDULED, "2026-07-03", "10:00", "13:00", "54.00", "0", null),
            block(null, "2026-07-04", "08:00", "11:00", "54.00", "6.00", null),
            block(BlockStatus.FORFEITED, "2026-07-05", "10:00", "13:30", "0", "0", "10.0"));
    private static final List<Cost> COSTS = List.of(
            cost(CostCategory.FUEL, "30.10"), cost(CostCategory.FUEL, "12.40"),
            cost(CostCategory.MAINTENANCE, "100.00"), cost(CostCategory.TOLL, "3.50"), cost(CostCategory.PARKING, "4.00"));

    @Test
    void standardMileageUsesEachBlocksDateRate() {
        NetEarnings result = NetEarnings.calculate(BLOCKS, COSTS, VehicleCostMethod.STANDARD_MILEAGE, MileageRates.irs());

        assertEquals(6, result.shifts());
        assertEquals(new BigDecimal("228.00"), result.grossEarnings());
        assertEquals(630, result.minutesWorked());
        assertEquals(new BigDecimal("105.0"), result.miles());
        // 40 miles at $0.725 before July 1, then 65 miles at $0.760.
        assertEquals(new BigDecimal("78.40"), result.mileageCost());
        assertEquals(new BigDecimal("78.40"), result.vehicleCost());
        assertEquals(new BigDecimal("7.50"), result.outOfPocketExpenses());
        assertEquals(new BigDecimal("85.90"), result.totalDeductions());
        assertEquals(new BigDecimal("150.00"), result.cashSpent());
        assertEquals(new BigDecimal("142.10"), result.netEarnings());
        assertEquals(new BigDecimal("21.71"), result.grossHourlyRate());
        assertEquals(new BigDecimal("13.53"), result.netHourlyRate());
        assertEquals(new BigDecimal("23.68"), result.netPerShift());
        assertEquals(new BigDecimal("2.17"), result.earningsPerMile());
        assertEquals(new BigDecimal("62.3"), result.netMargin());
        assertEquals(1, result.blocksWithoutMiles());
        assertEquals(new BigDecimal("42.50"), result.expenseTotals().get(CostCategory.FUEL));
        assertEquals(new BigDecimal("0.00"), result.expenseTotals().get(CostCategory.OTHER));
        assertEquals(5, result.expenseTotals().size());
    }

    @Test
    void actualExpensesCountFuelAndMaintenanceInsteadOfMiles() {
        NetEarnings result = NetEarnings.calculate(BLOCKS, COSTS, VehicleCostMethod.ACTUAL_EXPENSES, MileageRates.irs());

        assertEquals(new BigDecimal("78.40"), result.mileageCost(), "still shown for comparison");
        assertEquals(new BigDecimal("142.50"), result.vehicleCost());
        assertEquals(new BigDecimal("150.00"), result.totalDeductions());
        assertEquals(new BigDecimal("78.00"), result.netEarnings());
        assertEquals(new BigDecimal("7.43"), result.netHourlyRate());
        assertEquals(new BigDecimal("13.00"), result.netPerShift());
        assertEquals(new BigDecimal("34.2"), result.netMargin());
    }

    @Test
    void anAccountRateAppliesToEveryDate() {
        NetEarnings result = NetEarnings.calculate(BLOCKS, COSTS, VehicleCostMethod.STANDARD_MILEAGE,
                MileageRates.fixed(new BigDecimal("0.65")));

        assertEquals(new BigDecimal("68.25"), result.mileageCost());
        assertEquals(new BigDecimal("152.25"), result.netEarnings());
    }

    @Test
    void noRatesMeansNoMileageCost() {
        assertEquals(new BigDecimal("0.00"),
                NetEarnings.calculate(BLOCKS, List.of(), VehicleCostMethod.STANDARD_MILEAGE, null).mileageCost());
    }

    @Test
    void nothingToCountGivesZerosAndNoPerMileFigure() {
        NetEarnings result = NetEarnings.calculate(List.of(), List.of(), VehicleCostMethod.STANDARD_MILEAGE, MileageRates.irs());

        assertEquals(0, result.shifts());
        assertEquals(new BigDecimal("0.00"), result.grossEarnings());
        assertEquals(new BigDecimal("0.0"), result.miles());
        assertEquals(new BigDecimal("0.00"), result.grossHourlyRate());
        assertEquals(new BigDecimal("0.00"), result.netPerShift());
        assertEquals(new BigDecimal("0.0"), result.netMargin());
        assertNull(result.earningsPerMile());
    }

    @Test
    void expenseTotalsCannotBeChanged() {
        NetEarnings result = NetEarnings.calculate(BLOCKS, COSTS, VehicleCostMethod.STANDARD_MILEAGE, MileageRates.irs());

        assertThrows(UnsupportedOperationException.class, () -> result.expenseTotals().put(CostCategory.TOLL, BigDecimal.ONE));
    }

    private static Block block(BlockStatus status, String date, String start, String end, String base, String tips,
            String miles) {
        return new Block(status, "DSE4", LocalDate.parse(date), LocalTime.parse(start), LocalTime.parse(end),
                new BigDecimal(base), new BigDecimal(tips), miles == null ? null : new BigDecimal(miles));
    }

    private static Cost cost(CostCategory category, String amount) {
        return new Cost(category, new BigDecimal(amount), LocalDate.of(2026, 7, 1));
    }
}
