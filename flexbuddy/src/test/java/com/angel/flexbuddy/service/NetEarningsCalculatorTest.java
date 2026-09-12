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
import com.angel.flexbuddy.model.VehicleCostMethod;

class NetEarningsCalculatorTest {
    private final NetEarningsCalculator calculator = new NetEarningsCalculator();

    @Test
    void standardMileageUsesMilesAndOnlyAddsNonVehicleCashCosts() {
        Shift shift = shift("100", "20", "40.0");
        var result = calculator.calculate(List.of(shift), List.of(
                expense(ExpenseCategory.FUEL, "30"), expense(ExpenseCategory.TOLL, "5"),
                expense(ExpenseCategory.MAINTENANCE, "10"), expense(ExpenseCategory.OTHER, "2")),
                VehicleCostMethod.STANDARD_MILEAGE, new BigDecimal("0.700"));

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
                new BigDecimal("0.700"));

        assertThat(result.vehicleCost()).isEqualByComparingTo("40.00");
        assertThat(result.totalDeductions()).isEqualByComparingTo("45.00");
        assertThat(result.netEarnings()).isEqualByComparingTo("55.00");
        assertThat(result.earningsPerMile()).isNull();
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
