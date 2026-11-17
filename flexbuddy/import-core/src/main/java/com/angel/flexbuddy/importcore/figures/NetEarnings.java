package com.angel.flexbuddy.importcore.figures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Gross, costs and net for a set of blocks and the expenses that count with them. Scheduled blocks add nothing (see
 * {@link EarningsRules}). The consumer passes only the signed-in account's live blocks and countable expenses.
 */
public record NetEarnings(int shifts, BigDecimal grossEarnings, int minutesWorked, BigDecimal miles,
        BigDecimal mileageCost, Map<CostCategory, BigDecimal> expenseTotals, BigDecimal vehicleCost,
        BigDecimal outOfPocketExpenses, BigDecimal totalDeductions, BigDecimal cashSpent, BigDecimal netEarnings,
        BigDecimal grossHourlyRate, BigDecimal netHourlyRate, BigDecimal netPerShift, BigDecimal earningsPerMile,
        BigDecimal netMargin, int blocksWithoutMiles) {

    public static NetEarnings calculate(List<Block> blocks, List<Cost> costs, VehicleCostMethod method,
            MileageRates rates) {
        BigDecimal gross = BigDecimal.ZERO;
        int minutes = 0;
        BigDecimal miles = BigDecimal.ZERO;
        BigDecimal mileage = BigDecimal.ZERO;
        int withoutMiles = 0;
        for (Block block : blocks) {
            gross = gross.add(block.earnedPay());
            minutes += block.workedMinutes();
            BigDecimal counted = block.countedMiles();
            if (counted != null) miles = miles.add(counted);
            mileage = mileage.add(mileage(block, rates));
            if (block.countsTowardHours() && block.miles() == null) withoutMiles++;
        }
        gross = money(gross);
        miles = miles.setScale(1, RoundingMode.HALF_UP);
        BigDecimal mileageCost = money(mileage);
        EnumMap<CostCategory, BigDecimal> categories = new EnumMap<>(CostCategory.class);
        for (CostCategory category : CostCategory.values()) categories.put(category, money(BigDecimal.ZERO));
        for (Cost cost : costs) {
            categories.put(cost.category(), money(categories.get(cost.category()).add(cost.amount())));
        }
        BigDecimal actualVehicle = categories.get(CostCategory.FUEL).add(categories.get(CostCategory.MAINTENANCE));
        BigDecimal vehicleCost = method == VehicleCostMethod.ACTUAL_EXPENSES ? actualVehicle : mileageCost;
        BigDecimal outOfPocket = categories.get(CostCategory.TOLL).add(categories.get(CostCategory.PARKING))
                .add(categories.get(CostCategory.OTHER));
        BigDecimal cashSpent = BigDecimal.ZERO;
        for (BigDecimal total : categories.values()) cashSpent = cashSpent.add(total);
        BigDecimal deductions = money(vehicleCost.add(outOfPocket));
        BigDecimal net = money(gross.subtract(deductions));
        int count = blocks.size();
        return new NetEarnings(count, gross, minutes, miles, mileageCost, Collections.unmodifiableMap(categories),
                money(vehicleCost), money(outOfPocket), deductions, money(cashSpent), net,
                hourly(gross, minutes), hourly(net, minutes), divide(net, count),
                miles.signum() == 0 ? null : gross.divide(miles, 2, RoundingMode.HALF_UP),
                gross.signum() == 0 ? BigDecimal.ZERO.setScale(1)
                        : net.multiply(BigDecimal.valueOf(100)).divide(gross, 1, RoundingMode.HALF_UP),
                withoutMiles);
    }

    /** One block's counted miles at its date's rate, unrounded; the total is rounded once. */
    private static BigDecimal mileage(Block block, MileageRates rates) {
        BigDecimal counted = block.countedMiles();
        if (counted == null || rates == null) return BigDecimal.ZERO;
        BigDecimal rate = rates.rateOn(block.date());
        return rate == null ? BigDecimal.ZERO : counted.multiply(rate);
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
