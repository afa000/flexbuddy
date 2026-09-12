package com.angel.flexbuddy.model;

import java.time.Instant;

public interface Timestamped {
    Instant getCreatedAt();
    void setCreatedAt(Instant createdAt);
    Instant getUpdatedAt();
    void setUpdatedAt(Instant updatedAt);
}
