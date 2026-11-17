package com.angel.flexbuddy.importcore.figures;

/** A block's status, with the same names as the server's. A missing status is read as {@link #COMPLETED}. */
public enum BlockStatus {
    SCHEDULED, COMPLETED, CANCELLED, FORFEITED
}
