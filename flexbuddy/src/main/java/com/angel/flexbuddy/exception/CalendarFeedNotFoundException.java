package com.angel.flexbuddy.exception;

public class CalendarFeedNotFoundException extends LocalizedException {

    public CalendarFeedNotFoundException() {
        super("error.calendar.feedNotFound", new Object[0]);
    }
}
