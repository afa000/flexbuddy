package com.angel.flexbuddy.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

public class Shift {

        private int id;
    private String Station;
    private LocalDate Date;
    private LocalTime StartTime;
    private LocalTime EndTime;
    private BigDecimal BasePay;
    private BigDecimal Tips;
    private BigDecimal TotalPay;

    public ShiftService(int id, String station, LocalDate date, LocalTime startTime, LocalTime endTime, BigDecimal basePay, BigDecimal tips) {
        this.id = id;
        this.Station = station;
        this.Date = date;
        this.StartTime = startTime;
        this.EndTime = endTime;
        this.BasePay = basePay;
        this.Tips = tips;
        this.TotalPay = basePay.add(tips);
    }

    public int getId() {
        return id;
    }

    public String getStation() {
        return Station;
    }   

    public LocalDate getDate() {
        return Date;
    }

    public LocalTime getStartTime() {
        return StartTime;
    }

    public LocalTime getEndTime() {
        return EndTime;
    }

    public BigDecimal getBasePay() {
        return BasePay;
    }

    public BigDecimal getTips() {
        return Tips;
    }

    public BigDecimal getTotalPay() {
        return TotalPay;
    }

    public void setId(int id) {
        this.id = id;
    }

    public void setStation(String station) {
        this.Station = station;
    }

    public void setDate(LocalDate date) {
        this.Date = date;
    }

    public void setStartTime(LocalTime startTime) {
        this.StartTime = startTime;
    }

    public void setEndTime(LocalTime endTime) {
        this.EndTime = endTime;
    }

    public void setBasePay(BigDecimal basePay) {
        this.BasePay = basePay;
    }

    public void setTips(BigDecimal tips) {
        this.Tips = tips;
    }

    public void setTotalPay(BigDecimal totalPay) {
        this.TotalPay = totalPay;
    }

}
