package com.angel.flexbuddy.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id")
    private AppUser owner;

    public Shift(
            Long id,
            String station,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal basePay,
            BigDecimal tips
    ) {
        this(id, station, date, startTime, endTime, basePay, tips, null);
    }

    public Shift(
            Long id,
            String station,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            BigDecimal basePay,
            BigDecimal tips,
            AppUser owner
    ) {
        this.id = id;
        this.station = station;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.basePay = basePay;
        this.tips = tips;
        this.owner = owner;
    }

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
