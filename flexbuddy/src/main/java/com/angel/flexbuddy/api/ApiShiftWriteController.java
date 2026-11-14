package com.angel.flexbuddy.api;

import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.exception.InvalidShiftException;
import com.angel.flexbuddy.exception.ShiftConflictException;
import com.angel.flexbuddy.exception.ShiftNotFoundException;

import static com.angel.flexbuddy.api.ApiShiftWriteService.Action.*;
import com.angel.flexbuddy.api.ApiWriteReceipts.Acknowledgement;
import com.angel.flexbuddy.api.ApiShiftWriteService.Version;

@RestController
@RequestMapping("/api/v1/shifts")
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiShiftWriteController {
    private final ApiShiftWriteService service;

    public ApiShiftWriteController(ApiShiftWriteService service) { this.service = service; }

    @PostMapping
    ResponseEntity<Acknowledgement> create(Principal principal, @RequestBody ApiShiftInput input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var result = service.write(principal.getName(), CREATE, null, key, input, null);
        return ResponseEntity.created(URI.create("/api/v1/shifts/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    Acknowledgement edit(Principal principal, @PathVariable long id, @RequestBody ApiShiftInput input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.write(principal.getName(), EDIT, id, key, input, input.expectedUpdatedAt());
    }

    @PatchMapping("/{id}/status")
    Acknowledgement status(Principal principal, @PathVariable long id, @RequestBody ApiShiftInput input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.write(principal.getName(), STATUS, id, key, input, input.expectedUpdatedAt());
    }

    @PostMapping("/{id}/start")
    Acknowledgement start(Principal principal, @PathVariable long id, @RequestBody Version input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.write(principal.getName(), START, id, key, null, input.expectedUpdatedAt());
    }

    @PostMapping("/{id}/not-worked")
    Acknowledgement notWorked(Principal principal, @PathVariable long id, @RequestBody Version input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.write(principal.getName(), NOT_WORKED, id, key, null, input.expectedUpdatedAt());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(Principal principal, @PathVariable long id,
            @RequestParam(required = false) Instant expectedUpdatedAt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        service.write(principal.getName(), DELETE, id, key, null, expectedUpdatedAt);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    Acknowledgement restore(Principal principal, @PathVariable long id, @RequestBody Version input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.write(principal.getName(), RESTORE, id, key, null, input.expectedUpdatedAt());
    }

    @ExceptionHandler(ShiftNotFoundException.class)
    ResponseEntity<?> notFound() { return ResponseEntity.status(404).body(Map.of("error", "not_found")); }

    @ExceptionHandler(ShiftConflictException.class)
    ResponseEntity<?> conflict(ShiftConflictException exception) {
        return ResponseEntity.status(409).body(Map.of("error", "conflict", "current", ApiShift.of(exception.getCurrent())));
    }

    @ExceptionHandler(ApiWriteReceipts.KeyConflict.class)
    ResponseEntity<?> keyConflict() {
        return ResponseEntity.status(409).body(Map.of("error", "idempotency_conflict"));
    }

    @ExceptionHandler(ApiWriteReceipts.InvalidWrite.class)
    ResponseEntity<?> invalidFields(ApiWriteReceipts.InvalidWrite exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid", "fields", exception.fields()));
    }

    @ExceptionHandler(InvalidShiftException.class)
    ResponseEntity<?> invalidShift(InvalidShiftException exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid", "reason", exception.messageKey()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            InvalidFilterException.class})
    ResponseEntity<?> invalid() { return ResponseEntity.badRequest().body(Map.of("error", "invalid")); }
}
