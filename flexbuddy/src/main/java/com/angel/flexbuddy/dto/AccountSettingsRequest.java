package com.angel.flexbuddy.dto;

import java.math.BigDecimal;

import com.angel.flexbuddy.model.VehicleCostMethod;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

public record AccountSettingsRequest(
        @NotNull VehicleCostMethod vehicleCostMethod,
        @DecimalMin("0.000") @DecimalMax("5.000") @Digits(integer = 1, fraction = 3) BigDecimal mileageRate
) {}
