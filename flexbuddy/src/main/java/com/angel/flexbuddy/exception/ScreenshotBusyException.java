package com.angel.flexbuddy.exception;

/** Another screenshot is still being read; asking again in a moment will work. */
public class ScreenshotBusyException extends LocalizedException {

    public ScreenshotBusyException(String messageKey, Object... messageArgs) {
        super(messageKey, messageArgs);
    }
}
