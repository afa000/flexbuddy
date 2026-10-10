package com.angel.flexbuddy.exception;

public class InvalidStandingException extends LocalizedException {

    public InvalidStandingException(String messageKey, Object... messageArgs) {
        super(messageKey, messageArgs);
    }
}
