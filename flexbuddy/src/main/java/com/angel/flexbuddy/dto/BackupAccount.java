package com.angel.flexbuddy.dto;

import java.time.Instant;

public record BackupAccount(String displayName, String email, Instant createdAt) {
}
