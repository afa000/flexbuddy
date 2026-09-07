package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ShiftImportPreviewResponse {

    private String originalFilename;
    private String contentType;
    private long size;
    private String message;
    private String rawText;
    private int year;
    private String station;
    private LocalDate date;
    private LocalTime startTime;
    private LocalTime endTime;
    private BigDecimal basePay;
    private BigDecimal tips;
    private List<String> warnings;
}
