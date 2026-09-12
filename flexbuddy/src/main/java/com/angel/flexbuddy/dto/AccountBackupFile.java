package com.angel.flexbuddy.dto;

import java.time.Instant;
import java.util.List;

public record AccountBackupFile(
        String format,
        int version,
        Instant exportedAt,
        String appVersion,
        BackupAccount account,
        List<BackupShift> shifts,
        BackupCounts counts
) {
    public AccountBackupFile {
        shifts = shifts == null ? List.of() : List.copyOf(shifts);
    }
}
