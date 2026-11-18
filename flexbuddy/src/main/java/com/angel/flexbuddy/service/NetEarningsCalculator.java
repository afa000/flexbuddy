package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.angel.flexbuddy.dto.NetEarningsResult;
import com.angel.flexbuddy.importcore.figures.CostCategory;
import com.angel.flexbuddy.importcore.figures.NetEarnings;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.VehicleCostMethod;

/** Gross, costs and net for blocks and their expenses, with the shared library's {@link NetEarnings}. */
@Service
public class NetEarningsCalculator {
    public NetEarningsResult calculate(List<Shift> shifts, List<Expense> expenses,
            VehicleCostMethod method, MileageRates rates) {
        NetEarnings result = figures(shifts, expenses, method, rates);
        EnumMap<ExpenseCategory, BigDecimal> totals = new EnumMap<>(ExpenseCategory.class);
        for (Map.Entry<CostCategory, BigDecimal> entry : result.expenseTotals().entrySet()) {
            totals.put(FigureInputs.category(entry.getKey()), entry.getValue());
        }
        return new NetEarningsResult(result.shifts(), result.grossEarnings(), result.minutesWorked(), result.miles(),
                result.mileageCost(), Map.copyOf(totals), result.vehicleCost(), result.outOfPocketExpenses(),
                result.totalDeductions(), result.cashSpent(), result.netEarnings(), result.grossHourlyRate(),
                result.netHourlyRate(), result.netPerShift(), result.earningsPerMile(), result.netMargin(),
                result.blocksWithoutMiles());
    }

    /** Shared result for other plain-input calculators; existing callers retain the server DTO. */
    NetEarnings figures(List<Shift> shifts, List<Expense> expenses, VehicleCostMethod method, MileageRates rates) {
        return NetEarnings.calculate(FigureInputs.blocks(shifts), FigureInputs.costs(expenses),
                FigureInputs.method(method), rates == null ? null : rates.figures());
    }
}
