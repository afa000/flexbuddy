package com.angel.flexbuddy.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

import com.angel.flexbuddy.dto.BlockDetailsRequest;
import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.model.ShiftStatus;

/** Flat API input, adapted to the website's validated requests without changing its JSON conventions. */
public record ApiShiftInput(String station, LocalDate date, LocalTime startTime, LocalTime endTime,
        BigDecimal basePay, BigDecimal tips, BigDecimal miles, String status,
        BlockDetailsRequest details, Instant expectedUpdatedAt) {

    ShiftStatus parsedStatus() {
        return status == null ? null : ShiftStatus.parse(status);
    }

    CreateShiftRequest create() {
        var request = new CreateShiftRequest(station, date, startTime, endTime, basePay, tips, miles, parsedStatus());
        request.setDetails(details);
        return request;
    }

    UpdateShiftRequest update() {
        var request = new UpdateShiftRequest(station, date, startTime, endTime, basePay, tips, miles, parsedStatus());
        request.setDetails(details);
        return request;
    }
}
