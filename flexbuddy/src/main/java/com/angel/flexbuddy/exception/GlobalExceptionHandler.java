package com.angel.flexbuddy.exception;


import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice 
public class GlobalExceptionHandler {

    @ExceptionHandler(ShiftNotFoundException.class)
    public ResponseEntity<String> handleShiftNotFoundException(ShiftNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidScreenshotException.class)
    public ResponseEntity<String> handleInvalidScreenshotException(InvalidScreenshotException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(ScreenshotOcrException.class)
    public ResponseEntity<String> handleScreenshotOcrException(ScreenshotOcrException exception) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
    }
}
