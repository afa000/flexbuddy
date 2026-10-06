package com.angel.flexbuddy.exception;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice 
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ShiftNotFoundException.class)
    public ResponseEntity<String> handleShiftNotFoundException(ShiftNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(TaxPaymentNotFoundException.class)
    public ResponseEntity<String> handleTaxPaymentNotFound(TaxPaymentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }

    @ExceptionHandler(ExpenseNotFoundException.class)
    public ResponseEntity<String> handleExpenseNotFound(ExpenseNotFoundException exception) {
        return ResponseEntity.status(404).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidScreenshotException.class)
    public ResponseEntity<String> handleInvalidScreenshotException(InvalidScreenshotException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(ScreenshotOcrException.class)
    public ResponseEntity<String> handleScreenshotOcrException(ScreenshotOcrException exception) {
        // This answered 500 without a log line, so it never reached the logs or the alerts.
        log.error("screenshot text extraction failed", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(exception.getMessage());
    }

    @ExceptionHandler(ScreenshotBusyException.class)
    public ResponseEntity<String> handleScreenshotBusyException(ScreenshotBusyException exception) {
        // A busy reader is expected, so it is not logged as an error and sends no alert.
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidFilterException.class)
    public ResponseEntity<String> handleInvalidFilterException(InvalidFilterException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidBackupException.class)
    public ResponseEntity<String> handleInvalidBackupException(InvalidBackupException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidShiftException.class)
    public ResponseEntity<String> handleInvalidShiftException(InvalidShiftException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidPayoutException.class)
    public ResponseEntity<String> handleInvalidPayout(InvalidPayoutException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidStandingException.class)
    public ResponseEntity<String> handleInvalidStanding(InvalidStandingException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(InvalidRequestIdException.class)
    public ResponseEntity<String> handleInvalidRequestId(InvalidRequestIdException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
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
    public ResponseEntity<?> handleDataIntegrity(org.springframework.dao.DataIntegrityViolationException exception) {
        Throwable cause = org.springframework.core.NestedExceptionUtils.getMostSpecificCause(exception);
        String message = cause.getMessage() == null ? "" : cause.getMessage().toLowerCase(java.util.Locale.ROOT);
        if (message.contains("uk_shift_owner_create_request") || message.contains("uk_expense_owner_create_request")) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(new com.angel.flexbuddy.dto.ConflictResponse("DUPLICATE", null));
        }
        log.error("a change could not be saved", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("The change could not be saved.");
    }

    @ExceptionHandler(InvalidPushSubscriptionException.class)
    public ResponseEntity<String> handleInvalidPushSubscription(InvalidPushSubscriptionException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(exception.getMessage());
    }

    @ExceptionHandler(CalendarFeedNotFoundException.class)
    public ResponseEntity<String> handleCalendarFeedNotFound(CalendarFeedNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(exception.getMessage());
    }
}
