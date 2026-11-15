package com.angel.flexbuddy.api;

import java.security.Principal;
import java.time.Duration;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

import com.angel.flexbuddy.exception.InvalidScreenshotException;
import com.angel.flexbuddy.exception.ScreenshotBusyException;
import com.angel.flexbuddy.exception.ScreenshotOcrException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * NA-07: {@code POST /api/v1/shifts/import-preview}. Errors are codes only and never echo the screenshot, its text or
 * its file name; every answer, success or failure, is marked not cacheable.
 */
@RestController
@RequestMapping("/api/v1/shifts")
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiImportController {
    private static final Logger log = LoggerFactory.getLogger(ApiImportController.class);

    private final ApiImportService service;
    private final long retryAfterSeconds;

    public ApiImportController(ApiImportService service,
            @Value("${flexbuddy.import.ocr-wait:20s}") Duration ocrWait) {
        this.service = service;
        this.retryAfterSeconds = Math.max(1, ocrWait.toSeconds());
    }

    @PostMapping("/import-preview")
    ResponseEntity<?> preview(Principal principal, HttpServletRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var multipart = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
        if (multipart == null) return answer(400, Map.of("error", "invalid"));
        MultipartFile screenshot = multipart.getFile("screenshot");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(service.preview(principal.getName(), key, screenshot));
    }

    @ExceptionHandler(ApiImportService.AlreadySaved.class)
    ResponseEntity<?> alreadySaved(ApiImportService.AlreadySaved exception) {
        return answer(409, Map.of("error", "already_saved", "id", exception.id()));
    }

    @ExceptionHandler(ApiImportService.AccountBusy.class)
    ResponseEntity<?> accountBusy() {
        return ResponseEntity.status(429).cacheControl(CacheControl.noStore())
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds))
                .body(Map.of("error", "busy_account"));
    }

    @ExceptionHandler(ApiWriteReceipts.KeyConflict.class)
    ResponseEntity<?> keyConflict() { return answer(409, Map.of("error", "idempotency_conflict")); }

    @ExceptionHandler(ApiWriteReceipts.InvalidWrite.class)
    ResponseEntity<?> invalidKey(ApiWriteReceipts.InvalidWrite exception) {
        return answer(400, Map.of("error", "invalid", "fields", exception.fields()));
    }

    @ExceptionHandler(InvalidScreenshotException.class)
    ResponseEntity<?> invalidScreenshot(InvalidScreenshotException exception) {
        return answer(400, Map.of("error", "invalid", "reason", exception.messageKey()));
    }

    /** A backstop: ApiUploadLimitFilter answers these before any controller runs. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<?> tooLarge() { return answer(413, Map.of("error", "too_large")); }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<?> invalidUpload() { return answer(400, Map.of("error", "invalid")); }

    @ExceptionHandler(ScreenshotBusyException.class)
    ResponseEntity<?> busy() {
        // A busy reader is expected, so it is not logged as an error, as on the website.
        return answer(503, Map.of("error", "busy"));
    }

    @ExceptionHandler(ScreenshotOcrException.class)
    ResponseEntity<?> readFailed(ScreenshotOcrException exception) {
        // The class names only: an exception's message or cause could carry text read from the screenshot.
        Throwable cause = exception.getCause();
        log.error("screenshot text extraction failed: {}{}", exception.getClass().getSimpleName(),
                cause == null ? "" : " caused by " + cause.getClass().getName());
        return answer(500, Map.of("error", "read_failed"));
    }

    private static ResponseEntity<?> answer(int status, Object body) {
        return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).body(body);
    }
}
