package com.angel.flexbuddy.exception;

public class ShiftNotFoundException extends RuntimeException {

    public ShiftNotFoundException(Long id) {
        super("Shift not found with id: " + id);
    }
}
