package com.angel.flexbuddy.dto;

import java.io.Serializable;

public record BackupCounts(int shifts, int deletedShifts) implements Serializable {
}
