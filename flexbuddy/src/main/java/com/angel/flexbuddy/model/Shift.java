package com.angel.flexbuddy.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Shift {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String station;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal basePay;
    private BigDecimal tips;

    public BigDecimal getTotalPay() {
        BigDecimal base;
        if (basePay != null) {
            base = basePay;
        } 
        else {
            base = BigDecimal.ZERO;
        }

        BigDecimal tip;
        if (tips != null) {
            tip = tips;
        } 
        else {
            tip = BigDecimal.ZERO;
        }

        return base.add(tip);
    }

    public int getTimeWorked() {

        int totalMinutes = (int) ChronoUnit.MINUTES.between(startTime, endTime);
        
        return totalMinutes;
    }

}
