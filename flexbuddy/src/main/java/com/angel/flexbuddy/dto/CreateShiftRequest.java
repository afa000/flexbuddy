package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import com.angel.flexbuddy.model.ShiftStatus;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@NoArgsConstructor
public class CreateShiftRequest {

    @NotBlank
    @Size(max = 255, message = "Station must be 255 characters or fewer.")
    private String station;

    @NotNull
    private LocalDate date;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;

    /** Zero is allowed because cancelled and forfeited blocks may pay nothing; ShiftStatus enforces the per-status rule. */
    @NotNull
    @PositiveOrZero
    private BigDecimal basePay;

    @NotNull
    @PositiveOrZero
    private BigDecimal tips;

    @PositiveOrZero
    @Digits(integer = 7, fraction = 1)
    private BigDecimal miles;

    private ShiftStatus status;

    public CreateShiftRequest(String station, LocalDate date, LocalTime startTime, LocalTime endTime,
            BigDecimal basePay, BigDecimal tips) {
        this(station, date, startTime, endTime, basePay, tips, null);
    }

    public CreateShiftRequest(String station, LocalDate date, LocalTime startTime, LocalTime endTime,
            BigDecimal basePay, BigDecimal tips, BigDecimal miles) {
        this(station, date, startTime, endTime, basePay, tips, miles, null);
    }

    public CreateShiftRequest(String station, LocalDate date, LocalTime startTime, LocalTime endTime,
            BigDecimal basePay, BigDecimal tips, BigDecimal miles, ShiftStatus status) {
        this.station = station;
        this.date = date;
        this.startTime = startTime;
        this.endTime = endTime;
        this.basePay = basePay;
        this.tips = tips;
        this.miles = miles;
        this.status = status;
    }
}
