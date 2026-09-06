package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
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
}
