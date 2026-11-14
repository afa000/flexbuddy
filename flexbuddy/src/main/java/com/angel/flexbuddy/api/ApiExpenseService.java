package com.angel.flexbuddy.api;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.api.ApiWriteReceipts.Acknowledgement;
import com.angel.flexbuddy.api.ApiWriteReceipts.InvalidWrite;
import com.angel.flexbuddy.exception.ExpenseNotFoundException;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;
import com.angel.flexbuddy.service.ExpenseService;

/**
 * Expenses for the native app (NA-06): change-only sync like blocks, and outbox writes through the website's
 * ExpenseService, so the same validation, linking and Recently deleted rules apply.
 */
@Service
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiExpenseService {
    public enum Action { EXPENSE_CREATE, EXPENSE_EDIT, EXPENSE_DELETE, EXPENSE_RESTORE }

    public record SyncAnswer(boolean full, String cursor, List<ApiExpense> expenses, List<Long> deleted) {
    }

    private final ExpenseRepository expenses;
    private final AppUserRepository users;
    private final ExpenseService service;
    private final ApiWriteReceipts receipts;
    private final Clock clock;

    public ApiExpenseService(ExpenseRepository expenses, AppUserRepository users, ExpenseService service,
            ApiWriteReceipts receipts, Clock clock) {
        this.expenses = expenses;
        this.users = users;
        this.service = service;
        this.receipts = receipts;
        this.clock = clock;
    }

    /** The same rules as block sync (FB-033): full when asked, too old or after a removal; otherwise changes only. */
    @Transactional(readOnly = true)
    public SyncAnswer sync(String email, Instant since) {
        // Read the cursor before the rows, so a change committed in between is sent again rather than skipped.
        Instant resetAt = users.findSyncResetAt(email);
        Instant latest = latest(expenses.latestSyncChange(email), resetAt);
        boolean full = since == null
                || since.isBefore(Instant.now(clock).minus(ApiShiftService.MAX_CURSOR_AGE))
                || (resetAt != null && since.isBefore(resetAt.plus(ApiShiftService.OVERLAP)));
        String cursor = SyncCursor.encode(latest);
        if (full) {
            return new SyncAnswer(true, cursor, expenses.findLiveForSync(email).stream().map(ApiExpense::of).toList(),
                    List.of());
        }
        List<ApiExpense> live = new ArrayList<>();
        List<Long> deleted = new ArrayList<>();
        for (Expense expense : expenses.findChangedSince(email, since.minus(ApiShiftService.OVERLAP))) {
            if (expense.getDeletedAt() == null) live.add(ApiExpense.of(expense));
            else deleted.add(expense.getId());
        }
        deleted.sort(null);
        return new SyncAnswer(false, cursor, live, deleted);
    }

    /** One expense not in Recently deleted; the website's not-found exception otherwise. */
    @Transactional(readOnly = true)
    public ApiExpense expense(String email, long id) {
        return expenses.findByIdAndOwnerEmailIgnoreCase(id, email).map(ApiExpense::of)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
    }

    @Transactional
    public Acknowledgement write(String email, Action action, Long id, String header, ApiExpenseInput input,
            Instant expectedUpdatedAt) {
        String key = receipts.key(header);
        var owner = receipts.lock(email);
        String hash = receipts.hash(action.name(), id, input, expectedUpdatedAt);
        var previous = receipts.previous(owner, key, hash);
        if (previous.isPresent()) return new Acknowledgement(previous.get().getExpenseId());
        if (action != Action.EXPENSE_CREATE) {
            if (expectedUpdatedAt == null) throw new InvalidWrite(Map.of("expectedUpdatedAt", "required"));
            Expense current = expenses.lockIncludingDeleted(email, id).orElseThrow(() -> new ExpenseNotFoundException(id));
            if (action == Action.EXPENSE_RESTORE) {
                if (current.getDeletedAt() == null || !current.getDeletedAt().isAfter(
                        Instant.now(clock).minus(Duration.ofDays(30)))) throw new ExpenseNotFoundException(id);
            } else if (current.getDeletedAt() != null) {
                throw new ExpenseNotFoundException(id);
            }
            if (!ApiWriteReceipts.sameVersion(current.getUpdatedAt(), expectedUpdatedAt)) {
                throw new ApiWriteReceipts.Stale(ApiExpense.of(current));
            }
        }
        long resultId = switch (action) {
            // The key also guards against a website create with the same key adding a second expense.
            case EXPENSE_CREATE -> service.create(email, receipts.valid(input.request()), key).id();
            case EXPENSE_EDIT -> service.update(email, id, receipts.valid(input.request())).id();
            case EXPENSE_DELETE -> { service.delete(email, id); yield id; }
            case EXPENSE_RESTORE -> { service.restore(email, id); yield id; }
        };
        receipts.save(owner, key, hash, receipt -> receipt.setExpenseId(resultId));
        return new Acknowledgement(resultId);
    }

    private static Instant latest(Instant change, Instant reset) {
        Instant latest = Instant.EPOCH;
        if (change != null && change.isAfter(latest)) latest = change;
        if (reset != null && reset.isAfter(latest)) latest = reset;
        return latest;
    }
}
