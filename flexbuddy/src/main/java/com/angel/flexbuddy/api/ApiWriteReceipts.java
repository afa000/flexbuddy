package com.angel.flexbuddy.api;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.angel.flexbuddy.model.ApiWriteReceipt;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.ApiWriteReceiptRepository;
import com.angel.flexbuddy.repository.AppUserRepository;

import jakarta.validation.Validator;
import tools.jackson.databind.ObjectMapper;

/**
 * The outbox rules every API write shares (NA-05, NA-06): a UUID Idempotency-Key, one lock per account, a receipt
 * looked up before anything else and saved in the write's own transaction. Callers run inside that transaction.
 */
@Component
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiWriteReceipts {
    /** A request field the API refuses; answered as 400 {@code invalid} with {@code fields}. */
    public static class InvalidWrite extends RuntimeException {
        private final Map<String, String> fields;
        public InvalidWrite(Map<String, String> fields) { this.fields = fields; }
        public Map<String, String> fields() { return fields; }
    }

    /** The key was already used for a different action, record or body; answered as 409 {@code idempotency_conflict}. */
    public static class KeyConflict extends RuntimeException { }

    /**
     * The record changed since the phone saw it; answered as 409 {@code conflict} with {@code current}, which is null
     * when nothing is there (a payout with nothing recorded).
     */
    public static class Stale extends RuntimeException {
        private final transient Object current;
        public Stale(Object current) { this.current = current; }
        public Object current() { return current; }
    }

    public record Acknowledgement(long id) { }

    private final AppUserRepository users;
    private final ApiWriteReceiptRepository receipts;
    private final ObjectMapper mapper;
    private final Validator validator;

    public ApiWriteReceipts(AppUserRepository users, ApiWriteReceiptRepository receipts, ObjectMapper mapper,
            Validator validator) {
        this.users = users;
        this.receipts = receipts;
        this.mapper = mapper;
        this.validator = validator;
    }

    /** The header as a lower-case UUID, or {@link InvalidWrite}. */
    public String key(String header) {
        if (header == null || !header.trim().matches(
                "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new InvalidWrite(Map.of("Idempotency-Key", "invalid"));
        }
        return header.trim().toLowerCase(Locale.ROOT);
    }

    /** One account lock serializes simultaneous API requests, including creates with the same key. */
    public AppUser lock(String email) {
        return users.lockByEmail(email).orElseThrow(() -> new IllegalStateException("Missing account"));
    }

    /** The receipt of an earlier success with this key, or empty; {@link KeyConflict} if it was another request. */
    public Optional<ApiWriteReceipt> previous(AppUser owner, String key, String hash) {
        var previous = receipts.findByOwnerIdAndRequestKey(owner.getId(), key);
        if (previous.isPresent() && !previous.get().getRequestHash().equals(hash)) throw new KeyConflict();
        return previous;
    }

    /** Records the success; {@code target} sets exactly one of the block, expense or payout date. */
    public void save(AppUser owner, String key, String hash, Consumer<ApiWriteReceipt> target) {
        var receipt = new ApiWriteReceipt();
        receipt.setOwner(owner);
        receipt.setRequestKey(key);
        receipt.setRequestHash(hash);
        target.accept(receipt);
        receipts.save(receipt);
    }

    /**
     * A digest of the action, the record it targets, the bound request and the version the phone saw. Hashing the
     * bound DTO means JSON property order and spacing don't matter on a retry. The payload itself is never stored.
     */
    public String hash(String action, Object target, Object input, Instant expected) {
        String value = action + ":" + target + ":" + mapper.writeValueAsString(input) + ":" + expected;
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /** The request if it passes its bean validation, otherwise {@link InvalidWrite} naming each bad field. */
    public <T> T valid(T request) {
        Map<String, String> fields = new java.util.TreeMap<>();
        for (var violation : validator.validate(request)) fields.put(violation.getPropertyPath().toString(), "invalid");
        if (!fields.isEmpty()) throw new InvalidWrite(fields);
        return request;
    }

    /** Whether the stored version is the one the phone saw, compared at the database's microsecond precision. */
    public static boolean sameVersion(Instant stored, Instant expected) {
        if (stored == null || expected == null) return stored == expected;
        return stored.truncatedTo(ChronoUnit.MICROS).equals(expected.truncatedTo(ChronoUnit.MICROS));
    }
}
