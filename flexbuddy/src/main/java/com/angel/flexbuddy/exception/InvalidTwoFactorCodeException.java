package com.angel.flexbuddy.exception;

/** A setup or confirmation code that was not accepted. */
public class InvalidTwoFactorCodeException extends LocalizedException {

    public InvalidTwoFactorCodeException(String messageKey, Object... messageArgs) {
        super(messageKey, messageArgs);
    }
}
