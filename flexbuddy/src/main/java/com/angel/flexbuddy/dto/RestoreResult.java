package com.angel.flexbuddy.dto;

public record RestoreResult(int inserted, int skipped, int expensesInserted, int expensesSkipped, String batchId) {
    public RestoreResult(int inserted, int skipped, String batchId) { this(inserted, skipped, 0, 0, batchId); }
}
