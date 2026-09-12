package com.angel.flexbuddy.dto;

import java.time.Instant;
import java.util.List;

public record RestorePreviewResponse(
        String token,
        String format,
        int version,
        Instant exportedAt,
        String sourceEmail,
        boolean sameAccount,
        int total,
        int newShifts,
        int newDeletedShifts,
        int alreadyPresent,
        int inRecentlyDeleted,
        int invalid,
        int deletedInBackup,
        List<RestoreProblem> problems,
        int currentShifts
) {
}
