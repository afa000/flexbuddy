package com.angel.flexbuddy.exception;

import com.angel.flexbuddy.dto.ShiftResponse;

/** A change was made against an older version of a shift; carries the shift as it is now. */
public class ShiftConflictException extends RuntimeException {

    private final transient ShiftResponse current;

    public ShiftConflictException(ShiftResponse current) {
        super("The shift was changed after this change was made.");
        this.current = current;
    }

    public ShiftResponse getCurrent() {
        return current;
    }
}
