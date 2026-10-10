package com.angel.flexbuddy.exception;

public class InvalidFilterException extends LocalizedException {

    public InvalidFilterException(String messageKey, Object... messageArgs) {
        super(messageKey, messageArgs);
    }
}
