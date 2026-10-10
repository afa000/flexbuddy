package com.angel.flexbuddy.exception;

public class ScreenshotOcrException extends LocalizedException {

    public ScreenshotOcrException(Throwable cause, String messageKey, Object... messageArgs) {
        super(cause, messageKey, messageArgs);
    }
}
