package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.VehicleCostMethod;

class NetEarningsCalculatorTest {
    private final NetEarningsCalculator calculator = new NetEarningsCalculator();

    @Test
    void standardMileageUsesMilesAndOnlyAddsNonVehicleCashCosts() {
        Shift shift = shift("100", "20", "40.0");
        var result = calculator.calculate(List.of(shift), List.of(
                expense(ExpenseCategory.FUEL, "30"), expense(ExpenseCategory.TOLL, "5"),
                expense(ExpenseCategory.MAINTENANCE, "10"), expense(ExpenseCategory.OTHER, "2")),
                VehicleCostMethod.STANDARD_MILEAGE, MileageRates.fixed(new BigDecimal("0.700")));

        assertThat(result.grossEarnings()).isEqualByComparingTo("120.00");
        assertThat(result.mileageCost()).isEqualByComparingTo("28.00");
        assertThat(result.totalDeductions()).isEqualByComparingTo("35.00");
        assertThat(result.cashSpent()).isEqualByComparingTo("47.00");
        assertThat(result.netEarnings()).isEqualByComparingTo("85.00");
        assertThat(result.netHourlyRate()).isEqualByComparingTo("21.25");
    }

    @Test
    void actualExpensesUseFuelAndMaintenanceAndZeroMilesRemainSafe() {
        Shift shift = shift("100", "0", null);
        var result = calculator.calculate(List.of(shift), List.of(
                expense(ExpenseCategory.FUEL, "30"), expense(ExpenseCategory.MAINTENANCE, "10"),
                expense(ExpenseCategory.PARKING, "5")), VehicleCostMethod.ACTUAL_EXPENSES,
                MileageRates.fixed(new BigDecimal("0.700")));

        assertThat(result.vehicleCost()).isEqualByComparingTo("40.00");
        assertThat(result.totalDeductions()).isEqualByComparingTo("45.00");
        assertThat(result.netEarnings()).isEqualByComparingTo("55.00");
        assertThat(result.earningsPerMile()).isNull();
    }

    @Test
    void theIrsRateIsAppliedToEachBlocksOwnDate() {
        Shift spring = shiftOn(LocalDate.of(2026, 3, 10), "100.0");
        Shift summer = shiftOn(LocalDate.of(2026, 8, 10), "100.0");

        assertThat(calculator.calculate(List.of(spring), List.of(), VehicleCostMethod.STANDARD_MILEAGE,
                MileageRates.irs()).mileageCost()).isEqualByComparingTo("72.50");
        assertThat(calculator.calculate(List.of(summer), List.of(), VehicleCostMethod.STANDARD_MILEAGE,
                MileageRates.irs()).mileageCost()).isEqualByComparingTo("76.00");
        var both = calculator.calculate(List.of(spring, summer), List.of(), VehicleCostMethod.STANDARD_MILEAGE,
                MileageRates.irs());
        assertThat(both.mileageCost()).isEqualByComparingTo("148.50");
        assertThat(both.miles()).isEqualByComparingTo("200.0");
    }

    @Test
    void a2025BlockStillCostsSeventyCentsAMile() {
        Shift block = shiftOn(LocalDate.of(2025, 12, 31), "100.0");

        assertThat(calculator.calculate(List.of(block), List.of(), VehicleCostMethod.STANDARD_MILEAGE,
                MileageRates.irs()).mileageCost()).isEqualByComparingTo("70.00");
    }

    @Test
    void aCustomRateAppliesToEveryDate() {
        MileageRates custom = MileageRates.fixed(new BigDecimal("0.650"));
        var result = calculator.calculate(
                List.of(shiftOn(LocalDate.of(2025, 5, 1), "100.0"), shiftOn(LocalDate.of(2026, 8, 10), "100.0")),
                List.of(), VehicleCostMethod.STANDARD_MILEAGE, custom);

        assertThat(result.mileageCost()).isEqualByComparingTo("130.00");
    }

    @Test
    void oneRateGivesTheSameResultAsMilesTimesThatRateRoundedOnce() {
        Shift first = shiftOn(LocalDate.of(2026, 9, 1), "12.3");
        Shift second = shiftOn(LocalDate.of(2026, 9, 2), "45.7");

        var result = calculator.calculate(List.of(first, second), List.of(), VehicleCostMethod.STANDARD_MILEAGE,
                MileageRates.irs());

        assertThat(result.mileageCost()).isEqualByComparingTo(
                new BigDecimal("58.0").multiply(new BigDecimal("0.760")).setScale(2, java.math.RoundingMode.HALF_UP));
    }

    @Test
    void actualExpensesIgnoreTheMileageRate() {
        Shift block = shiftOn(LocalDate.of(2026, 8, 10), "100.0");

        var result = calculator.calculate(List.of(block), List.of(expense(ExpenseCategory.FUEL, "30")),
                VehicleCostMethod.ACTUAL_EXPENSES, MileageRates.irs());

        assertThat(result.vehicleCost()).isEqualByComparingTo("30.00");
    }

    private Shift shiftOn(LocalDate date, String miles) {
        Shift shift = new Shift(1L, "VEA7", date, LocalTime.of(8, 0), LocalTime.of(12, 0),
                new BigDecimal("100"), BigDecimal.ZERO);
        shift.setMiles(new BigDecimal(miles));
        return shift;
    }

    @Test
    void countsOnlyWorkedBlocksWhoseMilesWereNeverEntered() {
        Shift never = shift("100", "20", null);
        Shift noMiles = shift("100", "0", "0.0");
        Shift withMiles = shift("100", "0", "10.0");
        Shift scheduled = shift("100", "0", null);
        scheduled.setStatus(ShiftStatus.SCHEDULED);
        Shift cancelled = shift("18", "0", null);
        cancelled.setStatus(ShiftStatus.CANCELLED);

        var result = calculator.calculate(List.of(never, noMiles, withMiles, scheduled, cancelled), List.of(),
                VehicleCostMethod.STANDARD_MILEAGE, MileageRates.fixed(new BigDecimal("0.700")));

        assertThat(result.blocksWithoutMiles()).isEqualTo(1);
    }

    @Test
    void countingBlocksWithoutMilesLeavesEveryOtherFigureUnchanged() {
        var result = calculator.calculate(List.of(shift("100", "20", null), shift("100", "0", "10.0")),
                List.of(expense(ExpenseCategory.FUEL, "30"), expense(ExpenseCategory.TOLL, "5")),
                VehicleCostMethod.STANDARD_MILEAGE, MileageRates.fixed(new BigDecimal("0.700")));

        assertThat(result.blocksWithoutMiles()).isEqualTo(1);
        assertThat(result.grossEarnings()).isEqualByComparingTo("220.00");
        assertThat(result.miles()).isEqualByComparingTo("10.0");
        assertThat(result.mileageCost()).isEqualByComparingTo("7.00");
        assertThat(result.vehicleCost()).isEqualByComparingTo("7.00");
        assertThat(result.totalDeductions()).isEqualByComparingTo("12.00");
        assertThat(result.cashSpent()).isEqualByComparingTo("35.00");
        assertThat(result.netEarnings()).isEqualByComparingTo("208.00");
        assertThat(result.netHourlyRate()).isEqualByComparingTo("26.00");
        assertThat(result.netPerShift()).isEqualByComparingTo("104.00");
        assertThat(result.earningsPerMile()).isEqualByComparingTo("22.00");
        assertThat(result.netMargin()).isEqualByComparingTo("94.5");
    }

    private Shift shift(String base, String tips, String miles) {
        Shift shift = new Shift(1L, "VEA7", LocalDate.of(2026, 9, 1), LocalTime.of(8, 0),
                LocalTime.of(12, 0), new BigDecimal(base), new BigDecimal(tips));
        shift.setMiles(miles == null ? null : new BigDecimal(miles));
        return shift;
    }

    private Expense expense(ExpenseCategory category, String amount) {
        Expense expense = new Expense();
        expense.setCategory(category);
        expense.setAmount(new BigDecimal(amount));
        return expense;
    }
}
