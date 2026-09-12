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
        int duplicateInBackup,
        int invalid,
        int deletedInBackup,
        List<RestoreProblem> problems,
        int currentShifts,
        int totalExpenses,
        int newExpenses,
        int duplicateExpenses,
        int deletedExpensesInBackup
) {
    public RestorePreviewResponse(String token, String format, int version, Instant exportedAt, String sourceEmail,
            boolean sameAccount, int total, int newShifts, int newDeletedShifts, int alreadyPresent,
            int inRecentlyDeleted, int duplicateInBackup, int invalid, int deletedInBackup,
            List<RestoreProblem> problems, int currentShifts) {
        this(token, format, version, exportedAt, sourceEmail, sameAccount, total, newShifts, newDeletedShifts,
                alreadyPresent, inRecentlyDeleted, duplicateInBackup, invalid, deletedInBackup, problems,
                currentShifts, 0, 0, 0, 0);
    }
}
