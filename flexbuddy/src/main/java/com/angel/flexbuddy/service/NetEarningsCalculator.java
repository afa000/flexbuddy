package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.angel.flexbuddy.dto.NetEarningsResult;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.VehicleCostMethod;

@Service
public class NetEarningsCalculator {
    public NetEarningsResult calculate(List<Shift> shifts, List<Expense> expenses,
            VehicleCostMethod method, BigDecimal mileageRate) {
        BigDecimal gross = money(shifts.stream().map(Shift::getTotalPay).reduce(BigDecimal.ZERO, BigDecimal::add));
        int minutes = shifts.stream().mapToInt(Shift::getTimeWorked).sum();
        BigDecimal miles = shifts.stream().map(Shift::getMiles).filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add).setScale(1, RoundingMode.HALF_UP);
        BigDecimal mileageCost = money(miles.multiply(mileageRate == null ? BigDecimal.ZERO : mileageRate));
        EnumMap<ExpenseCategory, BigDecimal> categories = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory category : ExpenseCategory.values()) categories.put(category, money(BigDecimal.ZERO));
        expenses.forEach(expense -> categories.compute(expense.getCategory(),
                (ignored, total) -> money(total.add(expense.getAmount()))));
        BigDecimal actualVehicle = categories.get(ExpenseCategory.FUEL).add(categories.get(ExpenseCategory.MAINTENANCE));
        BigDecimal vehicleCost = method == VehicleCostMethod.ACTUAL_EXPENSES ? actualVehicle : mileageCost;
        BigDecimal outOfPocket = categories.get(ExpenseCategory.TOLL).add(categories.get(ExpenseCategory.PARKING))
                .add(categories.get(ExpenseCategory.OTHER));
        BigDecimal cashSpent = categories.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal deductions = money(vehicleCost.add(outOfPocket));
        BigDecimal net = money(gross.subtract(deductions));
        return new NetEarningsResult(shifts.size(), gross, minutes, miles, mileageCost, Map.copyOf(categories),
                money(vehicleCost), money(outOfPocket), deductions, money(cashSpent), net,
                hourly(gross, minutes), hourly(net, minutes), divide(net, shifts.size()),
                miles.signum() == 0 ? null : gross.divide(miles, 2, RoundingMode.HALF_UP),
                gross.signum() == 0 ? BigDecimal.ZERO.setScale(1)
                        : net.multiply(BigDecimal.valueOf(100)).divide(gross, 1, RoundingMode.HALF_UP));
    }

    private static BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal hourly(BigDecimal value, int minutes) {
        return minutes == 0 ? BigDecimal.ZERO.setScale(2)
                : value.multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(minutes), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal divide(BigDecimal value, int count) {
        return count == 0 ? BigDecimal.ZERO.setScale(2)
                : value.divide(BigDecimal.valueOf(count), 2, RoundingMode.HALF_UP);
    }
}
