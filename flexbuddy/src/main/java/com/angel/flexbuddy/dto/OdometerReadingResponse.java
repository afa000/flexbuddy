package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/** The last odometer reading the driver recorded, used to fill in the next block's start reading. */
public record OdometerReadingResponse(BigDecimal reading, LocalDate date, String station) {
}
