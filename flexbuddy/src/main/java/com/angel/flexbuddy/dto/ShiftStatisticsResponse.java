package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.util.Map;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.VehicleCostMethod;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter 
@NoArgsConstructor 
@AllArgsConstructor 
public class ShiftStatisticsResponse {

    private int totalShifts;
    private BigDecimal totalBasePay;
    private BigDecimal totalTips;
    private BigDecimal totalEarnings;
    private BigDecimal averagePayPerShift;
    private int totalTimeWorked;
    private BigDecimal averageHourlyEarnings;
    private BigDecimal averageHourlyBasePay;
    private BigDecimal averageHourlyTips;
    private BigDecimal averageBasePerShift;
    private BigDecimal averageTipsPerShift;
    private BigDecimal tipsShareOfEarnings;
    private int averageShiftMinutes;
    private BigDecimal totalMiles;
    private BigDecimal mileageCost;
    private BigDecimal totalExpenses;
    private BigDecimal totalDeductions;
    private BigDecimal netEarnings;
    private BigDecimal netHourlyRate;
    private BigDecimal earningsPerMile;
    private BigDecimal netMargin;
    private Map<ExpenseCategory, BigDecimal> expensesByCategory;
    private BigDecimal vehicleCost;
    private BigDecimal outOfPocket;
    private BigDecimal netPerShift;
    private VehicleCostMethod vehicleCostMethod;
    private BigDecimal mileageRate;

    public ShiftStatisticsResponse(int totalShifts, BigDecimal totalBasePay, BigDecimal totalTips,
            BigDecimal totalEarnings, BigDecimal averagePayPerShift, int totalTimeWorked,
            BigDecimal averageHourlyEarnings, BigDecimal averageHourlyBasePay, BigDecimal averageHourlyTips,
            BigDecimal averageBasePerShift, BigDecimal averageTipsPerShift, BigDecimal tipsShareOfEarnings,
            int averageShiftMinutes) {
        this(totalShifts, totalBasePay, totalTips, totalEarnings, averagePayPerShift, totalTimeWorked,
                averageHourlyEarnings, averageHourlyBasePay, averageHourlyTips, averageBasePerShift,
                averageTipsPerShift, tipsShareOfEarnings, averageShiftMinutes, BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, totalEarnings, averageHourlyEarnings,
                null, BigDecimal.ZERO, Map.of(), BigDecimal.ZERO, BigDecimal.ZERO, averagePayPerShift,
                VehicleCostMethod.STANDARD_MILEAGE, BigDecimal.ZERO);
    }
}
