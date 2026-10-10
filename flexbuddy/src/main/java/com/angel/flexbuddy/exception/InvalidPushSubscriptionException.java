package com.angel.flexbuddy.exception;

public class InvalidPushSubscriptionException extends LocalizedException {

    public InvalidPushSubscriptionException(String messageKey, Object... messageArgs) {
        super(messageKey, messageArgs);
    }
}
