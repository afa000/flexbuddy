package com.angel.flexbuddy.exception;

/** A setup or confirmation code that was not accepted. */
public class InvalidTwoFactorCodeException extends RuntimeException {

    public InvalidTwoFactorCodeException(String message) {
        super(message);
    }
}
