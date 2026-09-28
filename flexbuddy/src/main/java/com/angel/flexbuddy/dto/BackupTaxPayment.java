package com.angel.flexbuddy.dto;

import java.io.Serializable;
import java.time.LocalDate;

public record BackupTaxPayment(int taxYear, Integer quarter, LocalDate paidOn, String amount, String note)
        implements Serializable {
}
