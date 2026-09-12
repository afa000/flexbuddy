package com.angel.flexbuddy.model;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

/**
 * Fills the audit timestamps on {@link Shift} without overwriting values that are already set.
 * Spring Data's auditing listener always stamps the created date on a new entity, which loses the
 * original timestamps when a backup is restored.
 */
@Component
public class ShiftTimestampListener {

    private final Clock clock;

    public ShiftTimestampListener(Clock clock) {
        this.clock = clock;
    }

    @PrePersist
    void onCreate(Shift shift) {
        Instant now = Instant.now(clock);
        if (shift.getCreatedAt() == null) shift.setCreatedAt(now);
        if (shift.getUpdatedAt() == null) shift.setUpdatedAt(shift.getCreatedAt());
    }

    @PreUpdate
    void onUpdate(Shift shift) {
        shift.setUpdatedAt(Instant.now(clock));
    }
}
