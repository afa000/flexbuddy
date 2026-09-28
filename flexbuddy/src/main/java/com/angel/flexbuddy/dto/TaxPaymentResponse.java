package com.angel.flexbuddy.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record TaxPaymentResponse(Long id, int taxYear, Integer quarter, LocalDate paidOn, BigDecimal amount, String note) {
}
