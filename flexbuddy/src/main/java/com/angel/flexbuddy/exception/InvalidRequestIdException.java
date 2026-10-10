package com.angel.flexbuddy.exception;

public class InvalidRequestIdException extends LocalizedException {

    public InvalidRequestIdException() {
        super("error.request.idNotUuid", new Object[0]);
    }
}
