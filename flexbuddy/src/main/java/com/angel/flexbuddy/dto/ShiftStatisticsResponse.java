package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
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
}
