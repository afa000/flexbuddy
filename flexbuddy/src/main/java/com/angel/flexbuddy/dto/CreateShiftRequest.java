package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Setter
@Getter
@AllArgsConstructor
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

    @NotNull
    @Positive
    private BigDecimal basePay;

    @NotNull
    @PositiveOrZero
    private BigDecimal tips;




}
