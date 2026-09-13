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
            throw new InvalidFilterException("status is required.");
        }
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new InvalidFilterException("Unknown status: " + value);
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

    /** Returns a message describing why the values are invalid for this status, or null when they are valid. */
    public String validate(BigDecimal basePay, BigDecimal tips, BigDecimal miles) {
        boolean hasTips = tips != null && tips.signum() != 0;
        return switch (this) {
            case SCHEDULED -> basePay == null || basePay.signum() <= 0 ? "A scheduled shift needs the offered pay."
                    : hasTips ? "A scheduled shift cannot have tips yet."
                    : miles != null ? "A scheduled shift cannot have miles yet." : null;
            case COMPLETED -> basePay == null || basePay.signum() <= 0
                    ? "A completed shift needs base pay greater than 0." : null;
            case CANCELLED -> basePay == null || basePay.signum() < 0
                    ? "A cancelled shift needs cancellation pay of 0 or more."
                    : hasTips ? "A cancelled shift cannot have tips." : null;
            case FORFEITED -> basePay == null || basePay.signum() < 0
                    ? "A forfeited shift needs base pay of 0 or more."
                    : hasTips ? "A forfeited shift cannot have tips." : null;
        };
    }

    public String label() {
        return name().toLowerCase(Locale.ROOT);
    }
}
