package com.angel.flexbuddy.api;

import static com.angel.flexbuddy.api.ApiExpenseService.Action.EXPENSE_CREATE;
import static com.angel.flexbuddy.api.ApiExpenseService.Action.EXPENSE_DELETE;
import static com.angel.flexbuddy.api.ApiExpenseService.Action.EXPENSE_EDIT;
import static com.angel.flexbuddy.api.ApiExpenseService.Action.EXPENSE_RESTORE;

import java.net.URI;
import java.security.Principal;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.angel.flexbuddy.api.ApiShiftWriteService.Version;
import com.angel.flexbuddy.api.ApiWriteReceipts.Acknowledgement;
import com.angel.flexbuddy.exception.ExpenseNotFoundException;
import com.angel.flexbuddy.exception.ShiftNotFoundException;

import tools.jackson.databind.ObjectMapper;

/** Expenses for the native app (NA-06); see docs/api-v1.md. */
@RestController
@RequestMapping("/api/v1/expenses")
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiExpenseController {
    private final ApiExpenseService service;
    private final ObjectMapper mapper;

    public ApiExpenseController(ApiExpenseService service, ObjectMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    ResponseEntity<byte[]> expenses(Principal principal, @RequestParam(required = false) String updatedSince,
            WebRequest request) {
        Instant since = updatedSince == null ? null : SyncCursor.decode(updatedSince);
        return ApiEtags.withEtag(mapper, service.sync(principal.getName(), since), request);
    }

    @GetMapping("/{id}")
    ResponseEntity<byte[]> expense(Principal principal, @PathVariable long id, WebRequest request) {
        return ApiEtags.withEtag(mapper, service.expense(principal.getName(), id), request);
    }

    @PostMapping
    ResponseEntity<Acknowledgement> create(Principal principal, @RequestBody ApiExpenseInput input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        var result = service.write(principal.getName(), EXPENSE_CREATE, null, key, input, null);
        return ResponseEntity.created(URI.create("/api/v1/expenses/" + result.id())).body(result);
    }

    @PutMapping("/{id}")
    Acknowledgement edit(Principal principal, @PathVariable long id, @RequestBody ApiExpenseInput input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.write(principal.getName(), EXPENSE_EDIT, id, key, input, input.expectedUpdatedAt());
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(Principal principal, @PathVariable long id,
            @RequestParam(required = false) Instant expectedUpdatedAt,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        service.write(principal.getName(), EXPENSE_DELETE, id, key, null, expectedUpdatedAt);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/restore")
    Acknowledgement restore(Principal principal, @PathVariable long id, @RequestBody Version input,
            @RequestHeader(value = "Idempotency-Key", required = false) String key) {
        return service.write(principal.getName(), EXPENSE_RESTORE, id, key, null, input.expectedUpdatedAt());
    }

    @ExceptionHandler(ExpenseNotFoundException.class)
    ResponseEntity<?> notFound() { return ResponseEntity.status(404).body(Map.of("error", "not_found")); }

    /** The block to link is not this account's, or is in Recently deleted. */
    @ExceptionHandler(ShiftNotFoundException.class)
    ResponseEntity<?> blockNotFound() {
        return ResponseEntity.status(404).body(Map.of("error", "not_found", "field", "shiftId"));
    }

    @ExceptionHandler(ApiWriteReceipts.Stale.class)
    ResponseEntity<?> conflict(ApiWriteReceipts.Stale exception) {
        Map<String, Object> body = new HashMap<>();
        body.put("error", "conflict");
        body.put("current", exception.current());
        return ResponseEntity.status(409).body(body);
    }

    @ExceptionHandler(ApiWriteReceipts.KeyConflict.class)
    ResponseEntity<?> keyConflict() {
        return ResponseEntity.status(409).body(Map.of("error", "idempotency_conflict"));
    }

    @ExceptionHandler(ApiWriteReceipts.InvalidWrite.class)
    ResponseEntity<?> invalidFields(ApiWriteReceipts.InvalidWrite exception) {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid", "fields", exception.fields()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class,
            SyncCursor.InvalidCursorException.class})
    ResponseEntity<?> invalid() { return ResponseEntity.badRequest().body(Map.of("error", "invalid")); }
}
