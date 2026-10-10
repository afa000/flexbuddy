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
        @DecimalMin(value = "0.01", message = "{validation.taxPercent.range}")
        @DecimalMax(value = "60", message = "{validation.taxPercent.range}")
        @Digits(integer = 2, fraction = 2) BigDecimal taxSetAsidePercent,
        Boolean remindTax
) {
    public TaxSettingsRequest(BigDecimal taxSetAsidePercent) {
        this(taxSetAsidePercent, null);
    }
}
