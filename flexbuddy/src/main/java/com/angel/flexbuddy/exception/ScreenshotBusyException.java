package com.angel.flexbuddy.exception;

/** Another screenshot is still being read; asking again in a moment will work. */
public class ScreenshotBusyException extends RuntimeException {

    public ScreenshotBusyException(String message) {
        super(message);
    }
}
