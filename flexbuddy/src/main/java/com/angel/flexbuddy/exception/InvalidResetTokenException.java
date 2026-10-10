package com.angel.flexbuddy.exception;

/** A password-reset link that is unknown, expired or already used. */
public class InvalidResetTokenException extends LocalizedException {

    public InvalidResetTokenException() {
        super("error.reset.linkExpired", new Object[0]);
    }
}
