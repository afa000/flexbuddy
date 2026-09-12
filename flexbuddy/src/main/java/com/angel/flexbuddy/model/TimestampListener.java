package com.angel.flexbuddy.model;

import java.time.Clock;
import java.time.Instant;

import org.springframework.stereotype.Component;

import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;

@Component
public class TimestampListener {
    private static Clock clock = Clock.systemUTC();

    public TimestampListener(Clock clock) {
        TimestampListener.clock = clock;
    }

    @PrePersist
    public void onCreate(Timestamped entity) {
        Instant now = Instant.now(clock);
        if (entity.getCreatedAt() == null) entity.setCreatedAt(now);
        if (entity.getUpdatedAt() == null) entity.setUpdatedAt(entity.getCreatedAt());
    }

    @PreUpdate
    public void onUpdate(Timestamped entity) {
        entity.setUpdatedAt(Instant.now(clock));
    }
}
