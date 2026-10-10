package com.angel.flexbuddy.exception;

public class ShiftNotFoundException extends LocalizedException {

    public ShiftNotFoundException(Long id) {
        super("error.shift.notFound", new Object[] {String.valueOf(id)});
    }
}
