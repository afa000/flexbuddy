package com.angel.flexbuddy.service;

import java.time.LocalDate;

public record ParseContext(LocalDate today, LocalDate inheritedDate) {
    public ParseContext {
        if (today == null) throw new IllegalArgumentException("today is required");
    }
}
