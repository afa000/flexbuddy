package com.angel.flexbuddy.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.ShiftStatusRequest;
import com.angel.flexbuddy.exception.ShiftConflictException;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.ApiWriteReceipt;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.ApiWriteReceiptRepository;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.service.ShiftService;

import jakarta.validation.Validator;
import tools.jackson.databind.ObjectMapper;

/** Atomic, durable outbox acknowledgements around the website's business rules. */
@Service
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiShiftWriteService {
    public enum Action { CREATE, EDIT, STATUS, START, NOT_WORKED, DELETE, RESTORE }

    /** Start, not-worked, delete and restore only need the version seen by the phone. */
    public record Version(Instant expectedUpdatedAt) { }
    public record Acknowledgement(long id) { }

    public static class InvalidWrite extends RuntimeException {
        private final Map<String, String> fields;
        public InvalidWrite(Map<String, String> fields) { this.fields = fields; }
        public Map<String, String> fields() { return fields; }
    }

    public static class KeyConflict extends RuntimeException { }

    private final AppUserRepository users;
    private final ShiftRepository shifts;
    private final ApiWriteReceiptRepository receipts;
    private final ShiftService service;
    private final ObjectMapper mapper;
    private final Validator validator;
    private final Clock clock;

    public ApiShiftWriteService(AppUserRepository users, ShiftRepository shifts,
            ApiWriteReceiptRepository receipts, ShiftService service, ObjectMapper mapper,
            Validator validator, Clock clock) {
        this.users = users;
        this.shifts = shifts;
        this.receipts = receipts;
        this.service = service;
        this.mapper = mapper;
        this.validator = validator;
        this.clock = clock;
    }

    @Transactional
    public Acknowledgement write(String email, Action action, Long id, String header,
            ApiShiftInput input, Instant expectedUpdatedAt) {
        String key = key(header);
        // One account lock serializes simultaneous API requests, including creates with the same UUID.
        var owner = users.lockByEmail(email).orElseThrow(() -> new IllegalStateException("Missing account"));
        String hash = hash(action, id, input, expectedUpdatedAt);
        var previous = receipts.findByOwnerIdAndRequestKey(owner.getId(), key).orElse(null);
        if (previous != null) {
            if (!previous.getRequestHash().equals(hash)) throw new KeyConflict();
            return new Acknowledgement(previous.getShiftId());
        }
        if (action != Action.CREATE) {
            if (expectedUpdatedAt == null) throw new InvalidWrite(Map.of("expectedUpdatedAt", "required"));
            Shift current = shifts.lockIncludingDeleted(email, id).orElseThrow(() -> new ShiftNotFoundException(id));
            if (action == Action.RESTORE) {
                if (current.getDeletedAt() == null || !current.getDeletedAt().isAfter(
                        Instant.now(clock).minus(Duration.ofDays(30)))) throw new ShiftNotFoundException(id);
            } else if (current.getDeletedAt() != null) {
                throw new ShiftNotFoundException(id);
            }
            if (!current.getUpdatedAt().truncatedTo(ChronoUnit.MICROS)
                    .equals(expectedUpdatedAt.truncatedTo(ChronoUnit.MICROS))) {
                if (action == Action.RESTORE) {
                    // The live-only service cannot read trash; use its shared mapper for the conflict.
                    throw new ShiftConflictException(service.toResponses(email, java.util.List.of(current)).getFirst());
                }
                throw new ShiftConflictException(service.getShift(email, id));
            }
        }
        long resultId = switch (action) {
            case CREATE -> service.createShift(email, valid(input.create()), key).getId();
            case EDIT -> service.updateShift(email, id, valid(input.update()), key).getId();
            case STATUS -> service.changeStatus(email, id, valid(new ShiftStatusRequest(input.parsedStatus(),
                    input.basePay(), input.tips(), input.miles(), input.details(), expectedUpdatedAt)), key).getId();
            case START -> service.startShift(email, id).getId();
            case NOT_WORKED -> service.markNotWorked(email, id, expectedUpdatedAt, key).getId();
            case DELETE -> { service.deleteShift(email, id); yield id; }
            case RESTORE -> { service.restoreShift(email, id); yield id; }
        };
        var receipt = new ApiWriteReceipt();
        receipt.setOwner(owner);
        receipt.setRequestKey(key);
        receipt.setRequestHash(hash);
        receipt.setShiftId(resultId);
        receipts.save(receipt);
        return new Acknowledgement(resultId);
    }

    private <T> T valid(T request) {
        Map<String, String> fields = new java.util.TreeMap<>();
        for (var violation : validator.validate(request)) fields.put(violation.getPropertyPath().toString(), "invalid");
        if (!fields.isEmpty()) throw new InvalidWrite(fields);
        return request;
    }

    private static String key(String header) {
        if (header == null || !header.trim().matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new InvalidWrite(Map.of("Idempotency-Key", "invalid"));
        }
        return header.trim().toLowerCase(Locale.ROOT);
    }

    private String hash(Action action, Long id, ApiShiftInput input, Instant expected) {
        // Hash the bound DTO, so JSON property order/spacing do not affect a retry. Never store the payload.
        String value = action.name() + ":" + id + ":" + mapper.writeValueAsString(input) + ":" + expected;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
