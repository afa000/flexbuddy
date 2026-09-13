package com.angel.flexbuddy.exception;

public class CalendarFeedNotFoundException extends RuntimeException {

    public CalendarFeedNotFoundException() {
        super("Calendar feed not found.");
    }
}
