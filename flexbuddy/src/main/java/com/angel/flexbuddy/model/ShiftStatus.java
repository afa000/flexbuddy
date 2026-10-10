package com.angel.flexbuddy.model;

import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

import com.angel.flexbuddy.exception.InvalidFilterException;

public enum ShiftStatus {
    SCHEDULED, COMPLETED, CANCELLED, FORFEITED;

    public static final Set<ShiftStatus> ALL = Set.of(values());
    public static final Set<ShiftStatus> HISTORY = Set.of(COMPLETED, CANCELLED, FORFEITED);
    public static final Set<ShiftStatus> EARNINGS = Set.of(COMPLETED, CANCELLED);

    public static ShiftStatus parse(String value) {
        if (value == null || value.isBlank()) {
            throw new InvalidFilterException("error.filter.statusRequired");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidFilterException("error.filter.unknownStatus", value);
        }
    }

    public static Set<ShiftStatus> parseSet(String value, Set<ShiftStatus> defaults) {
        if (value == null || value.isBlank()) return defaults;
        if (value.trim().equalsIgnoreCase("all")) return ALL;
        EnumSet<ShiftStatus> statuses = EnumSet.noneOf(ShiftStatus.class);
        for (String part : value.split(",")) {
            if (!part.isBlank()) statuses.add(parse(part));
        }
        return statuses.isEmpty() ? defaults : Set.copyOf(statuses);
    }

    /** Returns the message key saying why the values are invalid for this status, or null when they are valid. */
    public String problemKey(BigDecimal basePay, BigDecimal tips, BigDecimal miles) {
        boolean hasTips = tips != null && tips.signum() != 0;
        return switch (this) {
            case SCHEDULED -> basePay == null || basePay.signum() <= 0 ? "error.shift.scheduledNeedsPay"
                    : hasTips ? "error.shift.scheduledNoTips"
                    : miles != null ? "error.shift.scheduledNoMiles" : null;
            case COMPLETED -> basePay == null || basePay.signum() <= 0
                    ? "error.shift.completedNeedsPay" : null;
            case CANCELLED -> basePay == null || basePay.signum() < 0
                    ? "error.shift.cancelledNeedsPay"
                    : hasTips ? "error.shift.cancelledNoTips" : null;
            case FORFEITED -> basePay == null || basePay.signum() < 0
                    ? "error.shift.forfeitedNeedsPay"
                    : hasTips ? "error.shift.forfeitedNoTips" : null;
        };
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
