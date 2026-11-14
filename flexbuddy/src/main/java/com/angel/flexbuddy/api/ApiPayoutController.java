package com.angel.flexbuddy.api;

import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.angel.flexbuddy.api.ApiPayoutService.Action;
import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.exception.InvalidPayoutException;

import tools.jackson.databind.ObjectMapper;

/** Pay periods and recorded payouts for the native app (NA-06); see docs/api-v1.md. */
@RestController
@RequestMapping("/api/v1/payouts")
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiPayoutController {
    private final ApiPayoutService service;
    private final ObjectMapper mapper;

    public ApiPayoutController(ApiPayoutService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    /** Always the whole answer: the figures depend on blocks and settings, so there is no change-only form. */
    @GetMapping
    ResponseEntity<byte[]> payouts(Principal principal, @RequestParam(defaultValue = "2") int count,
            WebRequest request) {
        return ApiEtags.withEtag(mapper, service.payouts(principal.getName(), count), request);
    }

    @PutMapping("/{payoutDate}")
    Map<String, String> record(Principal principal, @PathVariable LocalDate payoutDate,
            @RequestBody ApiPayoutService.Input input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        service.write(principal.getName(), Action.PAYOUT_RECORD, payoutDate, key, input, input.expectedUpdatedAt());
        return Map.of("payoutDate", payoutDate.toString());
    }

    @DeleteMapping("/{payoutDate}")
    ResponseEntity<Void> remove(Principal principal, @PathVariable LocalDate payoutDate,
            @RequestParam(required = false) Instant expectedUpdatedAt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        service.write(principal.getName(), Action.PAYOUT_REMOVE, payoutDate, key, null, expectedUpdatedAt);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ApiWriteReceipts.Stale.class)
    ResponseEntity<?> conflict(ApiWriteReceipts.Stale exception) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "conflict");
        body.put("current", exception.current());
        return ResponseEntity.status(409).body(body);
    }

    /** The website recorded the same payout at the same moment; nothing was saved, so the phone reloads and retries. */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> recordedMeanwhile() {
        return ResponseEntity.status(409).body(Map.of("error", "conflict"));
    }

    @ExceptionHandler(ApiWriteReceipts.KeyConflict.class)
    ResponseEntity<?> keyConflict() {
        return ResponseEntity.status(409).body(Map.of("error", "idempotency_conflict"));
    }

    @ExceptionHandler(ApiWriteReceipts.InvalidWrite.class)
    ResponseEntity<?> invalidFields(ApiWriteReceipts.InvalidWrite exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid", "fields", exception.fields()));
    }

    /** Not yet paid out, or not a payout day: the website's rule, as a reason code. */
    @ExceptionHandler(InvalidPayoutException.class)
    ResponseEntity<?> invalidPayout(InvalidPayoutException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid", "reason", exception.messageKey()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            InvalidFilterException.class})
    ResponseEntity<?> invalid() { return ResponseEntity.badRequest().body(Map.of("error", "invalid")); }
}
