package com.angel.flexbuddy.dto;

import java.io.Serializable;
import java.time.Instant;

public record BackupAccount(String displayName, String email, Instant createdAt) implements Serializable {
}
