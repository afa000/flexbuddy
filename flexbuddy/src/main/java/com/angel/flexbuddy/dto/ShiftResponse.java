package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ShiftResponse {
    
    private Long id;
    private String station;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal basePay;
    private BigDecimal tips;
    private BigDecimal totalPay;
    private int timeWorked;
    private BigDecimal hourlyRate;
    private BigDecimal miles;
    private BigDecimal mileageCost;
    private BigDecimal earningsPerMile;
    private BigDecimal linkedExpenses;
    private BigDecimal netPay;
    private BigDecimal netHourlyRate;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant deletedAt;

    public ShiftResponse(Long id, String station, LocalDate date, LocalTime startTime, LocalTime endTime,
            BigDecimal basePay, BigDecimal tips, BigDecimal totalPay, int timeWorked, BigDecimal hourlyRate,
            Instant createdAt, Instant updatedAt, Instant deletedAt) {
        this(id, station, date, startTime, endTime, basePay, tips, totalPay, timeWorked, hourlyRate,
                null, BigDecimal.ZERO.setScale(2), null, BigDecimal.ZERO.setScale(2), totalPay, hourlyRate,
                createdAt, updatedAt, deletedAt);
    }
}
