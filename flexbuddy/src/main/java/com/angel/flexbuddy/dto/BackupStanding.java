package com.angel.flexbuddy.dto;

import java.io.Serializable;
import java.time.LocalDate;

/** A standing the driver logged. The level is a string so an unknown one is skipped on restore, not fatal. */
public record BackupStanding(LocalDate recordedOn, String level, String note) implements Serializable {
}
