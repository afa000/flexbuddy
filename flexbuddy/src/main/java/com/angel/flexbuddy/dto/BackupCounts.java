package com.angel.flexbuddy.dto;

import java.io.Serializable;

public record BackupCounts(int shifts, int deletedShifts, int expenses, int deletedExpenses) implements Serializable {
    public BackupCounts(int shifts, int deletedShifts) { this(shifts, deletedShifts, 0, 0); }
}
