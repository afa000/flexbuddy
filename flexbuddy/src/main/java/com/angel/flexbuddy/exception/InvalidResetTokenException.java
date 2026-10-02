package com.angel.flexbuddy.exception;

/** A password-reset link that is unknown, expired or already used. */
public class InvalidResetTokenException extends RuntimeException {

    public InvalidResetTokenException() {
        super("This link has expired or was already used.");
    }
}
