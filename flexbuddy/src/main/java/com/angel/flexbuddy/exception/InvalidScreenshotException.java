package com.angel.flexbuddy.exception;

public class InvalidScreenshotException extends RuntimeException {

    public InvalidScreenshotException(String message) {
        super(message);
    }

    public InvalidScreenshotException(String message, Throwable cause) {
        super(message, cause);
    }
}
