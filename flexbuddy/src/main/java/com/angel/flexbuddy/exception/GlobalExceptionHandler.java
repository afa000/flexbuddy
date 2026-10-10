package com.angel.flexbuddy.exception;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final MessageSource messages;

    public GlobalExceptionHandler(MessageSource messages) {
        this.messages = messages;
    }

    /** The exception's text in the driver's language. */
    private String text(Throwable exception, Locale locale) {
        return LocalizedMessage.text(exception, messages, locale);
    }

    @ExceptionHandler(ShiftNotFoundException.class)
    public ResponseEntity<String> handleShiftNotFoundException(ShiftNotFoundException exception, Locale locale) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(text(exception, locale));
    }

    @ExceptionHandler(TaxPaymentNotFoundException.class)
    public ResponseEntity<String> handleTaxPaymentNotFound(TaxPaymentNotFoundException exception, Locale locale) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(text(exception, locale));
    }

    @ExceptionHandler(ExpenseNotFoundException.class)
    public ResponseEntity<String> handleExpenseNotFound(ExpenseNotFoundException exception, Locale locale) {
        return ResponseEntity.status(404).body(text(exception, locale));
    }

    @ExceptionHandler(InvalidScreenshotException.class)
    public ResponseEntity<String> handleInvalidScreenshotException(InvalidScreenshotException exception, Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(text(exception, locale));
    }

    @ExceptionHandler(ScreenshotOcrException.class)
    public ResponseEntity<String> handleScreenshotOcrException(ScreenshotOcrException exception, Locale locale) {
        // This answered 500 without a log line, so it never reached the logs or the alerts.
        log.error("screenshot text extraction failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(text(exception, locale));
    }

    @ExceptionHandler(ScreenshotBusyException.class)
    public ResponseEntity<String> handleScreenshotBusyException(ScreenshotBusyException exception, Locale locale) {
        // A busy reader is expected, so it is not logged as an error and sends no alert.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(text(exception, locale));
    }

    @ExceptionHandler(InvalidFilterException.class)
    public ResponseEntity<String> handleInvalidFilterException(InvalidFilterException exception, Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(text(exception, locale));
    }

    @ExceptionHandler(InvalidBackupException.class)
    public ResponseEntity<String> handleInvalidBackupException(InvalidBackupException exception, Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(text(exception, locale));
    }

    @ExceptionHandler(InvalidShiftException.class)
    public ResponseEntity<String> handleInvalidShiftException(InvalidShiftException exception, Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(text(exception, locale));
    }

    @ExceptionHandler(InvalidPayoutException.class)
    public ResponseEntity<String> handleInvalidPayout(InvalidPayoutException exception, Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(text(exception, locale));
    }

    @ExceptionHandler(InvalidStandingException.class)
    public ResponseEntity<String> handleInvalidStanding(InvalidStandingException exception, Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(text(exception, locale));
    }

    @ExceptionHandler(InvalidRequestIdException.class)
    public ResponseEntity<String> handleInvalidRequestId(InvalidRequestIdException exception, Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(text(exception, locale));
    }

    @ExceptionHandler(ShiftConflictException.class)
    public ResponseEntity<com.angel.flexbuddy.dto.ConflictResponse> handleShiftConflict(ShiftConflictException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(new com.angel.flexbuddy.dto.ConflictResponse("CONFLICT", exception.getCurrent()));
    }

    /**
     * Two copies of the same create raced and the second hit the unique key: the first one landed, so the client
     * treats it as delivered. Any other constraint stays a plain failure, and its text is never returned.
     */
    @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)
    public ResponseEntity<?> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException exception, Locale locale) {
        Throwable cause = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(exception);
        String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (message.contains("uk_shift_owner_create_request") || message.contains("uk_expense_owner_create_request")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new com.angel.flexbuddy.dto.ConflictResponse("DUPLICATE", null));
        }
        log.error("a change could not be saved", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(messages.getMessage("error.changeNotSaved", null, locale));
    }

    @ExceptionHandler(InvalidPushSubscriptionException.class)
    public ResponseEntity<String> handleInvalidPushSubscription(InvalidPushSubscriptionException exception, Locale locale) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(text(exception, locale));
    }

    @ExceptionHandler(CalendarFeedNotFoundException.class)
    public ResponseEntity<String> handleCalendarFeedNotFound(CalendarFeedNotFoundException exception, Locale locale) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(text(exception, locale));
    }
}
