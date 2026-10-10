package com.angel.flexbuddy.exception;

public class InvalidPayoutException extends LocalizedException {

    public InvalidPayoutException(String messageKey, Object... messageArgs) {
        super(messageKey, messageArgs);
    }
}
