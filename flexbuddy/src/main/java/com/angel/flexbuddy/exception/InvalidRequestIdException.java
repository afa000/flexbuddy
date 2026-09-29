package com.angel.flexbuddy.exception;

public class InvalidRequestIdException extends RuntimeException {
    public InvalidRequestIdException() {
        super("Idempotency-Key must be a UUID.");
    }
}
