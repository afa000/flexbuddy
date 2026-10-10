package com.angel.flexbuddy.exception;

public class InvalidShiftException extends LocalizedException {

    public InvalidShiftException(String messageKey, Object... messageArgs) {
        super(messageKey, messageArgs);
    }
}
