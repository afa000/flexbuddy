package com.angel.flexbuddy.exception;

public class InvalidScreenshotException extends LocalizedException {

    public InvalidScreenshotException(String messageKey, Object... messageArgs) {
        super(messageKey, messageArgs);
    }

    public InvalidScreenshotException(Throwable cause, String messageKey, Object... messageArgs) {
        super(cause, messageKey, messageArgs);
    }
}
