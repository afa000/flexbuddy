package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;

/**
 * The share of net earnings to put aside for taxes, where null turns the reserve off, and whether to send due-date
 * reminders. Optional; when absent the saved reminder choice is kept. The two are independent.
 */
public record TaxSettingsRequest(
        @DecimalMin(value = "0.01", message = "The set-aside must be between 1 and 60 percent.")
        @DecimalMax(value = "60", message = "The set-aside must be between 1 and 60 percent.")
        @Digits(integer = 2, fraction = 2) BigDecimal taxSetAsidePercent,
        Boolean remindTax
) {
    public TaxSettingsRequest(BigDecimal taxSetAsidePercent) {
        this(taxSetAsidePercent, null);
    }
}
