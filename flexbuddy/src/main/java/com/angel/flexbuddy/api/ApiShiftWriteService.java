package com.angel.flexbuddy.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.api.ApiWriteReceipts.Acknowledgement;
import com.angel.flexbuddy.api.ApiWriteReceipts.InvalidWrite;
import com.angel.flexbuddy.dto.ShiftStatusRequest;
import com.angel.flexbuddy.exception.ShiftConflictException;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.service.ShiftService;

/** Atomic, durable outbox acknowledgements around the website's business rules. */
@Service
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiShiftWriteService {
    public enum Action { CREATE, EDIT, STATUS, START, NOT_WORKED, DELETE, RESTORE }

    /** Start, not-worked, delete and restore only need the version seen by the phone. */
    public record Version(Instant expectedUpdatedAt) { }

    private final ShiftRepository shifts;
    private final ApiWriteReceipts receipts;
    private final ShiftService service;
    private final Clock clock;

    public ApiShiftWriteService(ShiftRepository shifts, ApiWriteReceipts receipts, ShiftService service, Clock clock) {
        this.shifts = shifts;
        this.receipts = receipts;
        this.service = service;
        this.clock = clock;
    }

    @Transactional
    public Acknowledgement write(String email, Action action, Long id, String header,
            ApiShiftInput input, Instant expectedUpdatedAt) {
        String key = receipts.key(header);
        var owner = receipts.lock(email);
        String hash = receipts.hash(action.name(), id, input, expectedUpdatedAt);
        var previous = receipts.previous(owner, key, hash);
        if (previous.isPresent()) return new Acknowledgement(previous.get().getShiftId());
        if (action != Action.CREATE) {
            if (expectedUpdatedAt == null) throw new InvalidWrite(Map.of("expectedUpdatedAt", "required"));
            Shift current = shifts.lockIncludingDeleted(email, id).orElseThrow(() -> new ShiftNotFoundException(id));
            if (action == Action.RESTORE) {
                if (current.getDeletedAt() == null || !current.getDeletedAt().isAfter(
                        Instant.now(clock).minus(Duration.ofDays(30)))) throw new ShiftNotFoundException(id);
            } else if (current.getDeletedAt() != null) {
                throw new ShiftNotFoundException(id);
            }
            if (!ApiWriteReceipts.sameVersion(current.getUpdatedAt(), expectedUpdatedAt)) {
                if (action == Action.RESTORE) {
                    // The live-only service cannot read trash; use its shared mapper for the conflict.
                    throw new ShiftConflictException(service.toResponses(email, java.util.List.of(current)).getFirst());
                }
                throw new ShiftConflictException(service.getShift(email, id));
            }
        }
        long resultId = switch (action) {
            case CREATE -> service.createShift(email, receipts.valid(input.create()), key).getId();
            case EDIT -> service.updateShift(email, id, receipts.valid(input.update()), key).getId();
            case STATUS -> service.changeStatus(email, id, receipts.valid(new ShiftStatusRequest(input.parsedStatus(),
                    input.basePay(), input.tips(), input.miles(), input.details(), expectedUpdatedAt)), key).getId();
            case START -> service.startShift(email, id).getId();
            case NOT_WORKED -> service.markNotWorked(email, id, expectedUpdatedAt, key).getId();
            case DELETE -> { service.deleteShift(email, id); yield id; }
            case RESTORE -> { service.restoreShift(email, id); yield id; }
        };
        receipts.save(owner, key, hash, receipt -> receipt.setShiftId(resultId));
        return new Acknowledgement(resultId);
    }
}
