package com.angel.flexbuddy.dto;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record AccountBackupFile(
        String format,
        int version,
        Instant exportedAt,
        String appVersion,
        BackupAccount account,
        List<BackupShift> shifts,
        List<BackupExpense> expenses,
        BackupSettings settings,
        BackupCounts counts,
        List<BackupTaxPayment> taxPayments
) implements Serializable {
    public AccountBackupFile {
        shifts = shifts == null ? List.of() : List.copyOf(shifts);
        expenses = expenses == null ? List.of() : List.copyOf(expenses);
        taxPayments = taxPayments == null ? List.of() : List.copyOf(taxPayments);
    }

    /** Backups made before tax payments were recorded. */
    public AccountBackupFile(String format, int version, Instant exportedAt, String appVersion, BackupAccount account,
            List<BackupShift> shifts, List<BackupExpense> expenses, BackupSettings settings, BackupCounts counts) {
        this(format, version, exportedAt, appVersion, account, shifts, expenses, settings, counts, List.of());
    }

    public AccountBackupFile(String format, int version, Instant exportedAt, String appVersion,
            BackupAccount account, List<BackupShift> shifts, BackupCounts counts) {
        this(format, version, exportedAt, appVersion, account, shifts, List.of(), null, counts);
    }
}
