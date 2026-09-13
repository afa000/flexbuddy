package com.angel.flexbuddy.validation;

import java.time.ZoneId;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class TimeZoneValidator implements ConstraintValidator<ValidTimeZone, String> {

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        return value == null || ZoneId.getAvailableZoneIds().contains(value);
    }
}
