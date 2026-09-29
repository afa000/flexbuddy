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
        List<BackupTaxPayment> taxPayments,
        List<BackupPayout> payouts,
        List<BackupStanding> standing
) implements Serializable {
    public AccountBackupFile {
        shifts = shifts == null ? List.of() : List.copyOf(shifts);
        expenses = expenses == null ? List.of() : List.copyOf(expenses);
        taxPayments = taxPayments == null ? List.of() : List.copyOf(taxPayments);
        payouts = payouts == null ? List.of() : List.copyOf(payouts);
        standing = standing == null ? List.of() : List.copyOf(standing);
    }

    /** Backups made before standing was recorded. */
    public AccountBackupFile(String format, int version, Instant exportedAt, String appVersion, BackupAccount account,
            List<BackupShift> shifts, List<BackupExpense> expenses, BackupSettings settings, BackupCounts counts,
            List<BackupTaxPayment> taxPayments, List<BackupPayout> payouts) {
        this(format, version, exportedAt, appVersion, account, shifts, expenses, settings, counts, taxPayments, payouts,
                List.of());
    }

    /** Backups made before payouts were recorded. */
    public AccountBackupFile(String format, int version, Instant exportedAt, String appVersion, BackupAccount account,
            List<BackupShift> shifts, List<BackupExpense> expenses, BackupSettings settings, BackupCounts counts,
            List<BackupTaxPayment> taxPayments) {
        this(format, version, exportedAt, appVersion, account, shifts, expenses, settings, counts, taxPayments, List.of(),
                List.of());
    }

    /** Backups made before tax payments were recorded. */
    public AccountBackupFile(String format, int version, Instant exportedAt, String appVersion, BackupAccount account,
            List<BackupShift> shifts, List<BackupExpense> expenses, BackupSettings settings, BackupCounts counts) {
        this(format, version, exportedAt, appVersion, account, shifts, expenses, settings, counts, List.of(), List.of(),
                List.of());
    }

    public AccountBackupFile(String format, int version, Instant exportedAt, String appVersion,
            BackupAccount account, List<BackupShift> shifts, BackupCounts counts) {
        this(format, version, exportedAt, appVersion, account, shifts, List.of(), null, counts);
    }
}
