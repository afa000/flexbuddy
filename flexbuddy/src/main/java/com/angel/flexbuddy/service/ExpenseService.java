package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.ExpenseFilter;
import com.angel.flexbuddy.dto.ExpenseRequest;
import com.angel.flexbuddy.dto.ExpenseResponse;
import com.angel.flexbuddy.dto.ExpenseSummaryResponse;
import com.angel.flexbuddy.exception.ExpenseNotFoundException;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@Service
public class ExpenseService {
    private static final LocalDate EARLIEST = LocalDate.of(1, 1, 1);
    private static final LocalDate LATEST = LocalDate.of(9999, 12, 31);
    private final ExpenseRepository expenseRepository;
    private final ShiftRepository shiftRepository;
    private final AppUserRepository userRepository;
    private final Clock clock;

    public ExpenseService(ExpenseRepository expenseRepository, ShiftRepository shiftRepository,
            AppUserRepository userRepository, Clock clock) {
        this.expenseRepository = expenseRepository;
        this.shiftRepository = shiftRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getExpenses(String email, ExpenseFilter filter) {
        return toResponses(email, findFiltered(email, filter));
    }

    /** The block an expense is linked to, deleted or not. Status is never null; an old row without one was completed. */
    public record LinkedShift(Long id, String station, ShiftStatus status, boolean deleted) {
        /** An expense on a block that is still scheduled waits for it; one on a deleted block counts by its date. */
        boolean waiting() { return !deleted && status == ShiftStatus.SCHEDULED; }
    }

    /**
     * The blocks these expenses are linked to, by id, read in one query with deleted ones included. Expenses never
     * follow their link past its id, because a deleted block can't be loaded through it.
     */
    @Transactional(readOnly = true)
    public java.util.Map<Long, LinkedShift> linkedShifts(String email, java.util.Collection<Expense> expenses) {
        java.util.Set<Long> ids = expenses.stream().map(ExpenseService::linkedShiftId).filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());
        if (ids.isEmpty()) return java.util.Map.of();
        return shiftRepository.findLinkedIncludingDeleted(email, ids).stream()
                .collect(java.util.stream.Collectors.toMap(ShiftRepository.LinkedShiftRow::getId, row -> new LinkedShift(
                        row.getId(), row.getStation(),
                        row.getStatus() == null ? ShiftStatus.COMPLETED : ShiftStatus.valueOf(row.getStatus()),
                        Boolean.TRUE.equals(row.getDeleted()))));
    }

    /** The id of the block an expense is linked to, read without loading the block. */
    public static Long linkedShiftId(Expense expense) {
        return expense.getShift() == null ? null : expense.getShift().getId();
    }

    /** The linked block from a lookup, or null for an unlinked expense. */
    public static LinkedShift linkOf(Expense expense, java.util.Map<Long, LinkedShift> links) {
        Long id = linkedShiftId(expense);
        return id == null ? null : links.get(id);
    }

    /** The expenses that count toward net: all but those on a block that is still scheduled. */
    @Transactional(readOnly = true)
    public List<Expense> countable(String email, List<Expense> expenses) {
        java.util.Map<Long, LinkedShift> links = linkedShifts(email, expenses);
        return expenses.stream().filter(expense -> {
            LinkedShift link = linkOf(expense, links);
            return link == null || !link.waiting();
        }).toList();
    }

    /** The station an expense counts under: its block's, unless the block is deleted, when it counts as unlinked. */
    public static String countedStation(Expense expense, java.util.Map<Long, LinkedShift> links) {
        LinkedShift link = linkOf(expense, links);
        return link == null || link.deleted() ? null : link.station();
    }

    @Transactional(readOnly = true)
    public List<Expense> findFiltered(String email, ExpenseFilter filter) {
        return expenseRepository.findFiltered(email,
                filter.from() == null ? EARLIEST : filter.from(), filter.to() == null ? LATEST : filter.to(),
                filter.station() == null ? "" : filter.station(), filter.query() == null ? "" : filter.query(),
                filter.category(), filter.shiftId());
    }

    @Transactional(readOnly = true)
    public List<Expense> findForShifts(String email, List<Shift> shifts) {
        if (shifts.isEmpty()) return List.of();
        java.util.Set<Long> ids = shifts.stream().map(Shift::getId).collect(java.util.stream.Collectors.toSet());
        return findFiltered(email, new ExpenseFilter(null, null, null, null, null, null)).stream()
                .filter(expense -> expense.getShift() != null && ids.contains(expense.getShift().getId())).toList();
    }

    @Transactional(readOnly = true)
    public List<ExpenseResponse> getForShift(String email, Long shiftId) {
        ownedShift(email, shiftId);
        return toResponses(email, expenseRepository.findAllByShiftIdAndOwnerEmailIgnoreCaseOrderByDateDesc(shiftId, email));
    }

    @Transactional
    public ExpenseResponse create(String email, ExpenseRequest request) {
        return create(email, request, null);
    }

    /**
     * Creates an expense. With a request id, a repeat of the same create returns the expense the first one made,
     * even if it was deleted since, instead of adding another.
     */
    @Transactional
    public ExpenseResponse create(String email, ExpenseRequest request, String requestId) {
        if (requestId != null) {
            Expense existing = expenseRepository.findByCreateRequestIdIncludingDeleted(email, requestId).orElse(null);
            if (existing != null) return toResponse(email, existing);
        }
        AppUser owner = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));
        Expense expense = new Expense();
        expense.setCreateRequestId(requestId);
        expense.setOwner(owner);
        apply(expense, email, request);
        return toResponse(email, expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse update(String email, Long id, ExpenseRequest request) {
        Expense expense = expenseRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
        apply(expense, email, request);
        return toResponse(email, expenseRepository.save(expense));
    }

    @Transactional
    public String delete(String email, Long id) {
        String batch = UUID.randomUUID().toString();
        if (expenseRepository.softDelete(email, id, Instant.now(clock), batch) == 0) {
            throw new ExpenseNotFoundException(id);
        }
        return batch;
    }

    @Transactional
    public ExpenseResponse restore(String email, Long id) {
        if (expenseRepository.restoreDeleted(email, id) == 0) throw new ExpenseNotFoundException(id);
        return expenseRepository.findByIdAndOwnerEmailIgnoreCase(id, email).map(expense -> toResponse(email, expense))
                .orElseThrow(() -> new ExpenseNotFoundException(id));
    }

    @Transactional
    public int restoreBatch(String email, String batch) { return expenseRepository.restoreBatch(email, batch); }

    @Transactional
    public List<ExpenseResponse> getTrash(String email) {
        expenseRepository.purgeDeletedBefore(Instant.now(clock).minus(30, java.time.temporal.ChronoUnit.DAYS));
        return toResponses(email, expenseRepository.findTrash(email));
    }

    @Transactional
    public void permanentlyDelete(String email, Long id) {
        if (expenseRepository.permanentlyDelete(email, id) == 0) throw new ExpenseNotFoundException(id);
    }

    @Transactional
    public int emptyTrash(String email) { return expenseRepository.emptyTrash(email); }

    @Transactional(readOnly = true)
    public ExpenseSummaryResponse summary(String email, ExpenseFilter filter) {
        List<Expense> expenses = findFiltered(email, filter);
        EnumMap<ExpenseCategory, BigDecimal> totals = new EnumMap<>(ExpenseCategory.class);
        for (ExpenseCategory category : ExpenseCategory.values()) totals.put(category, BigDecimal.ZERO.setScale(2));
        expenses.forEach(expense -> totals.compute(expense.getCategory(),
                (category, amount) -> money(amount.add(expense.getAmount()))));
        BigDecimal total = totals.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new ExpenseSummaryResponse(money(total), expenses.size(), Map.copyOf(totals));
    }

    private void apply(Expense expense, String email, ExpenseRequest request) {
        expense.setDate(request.date());
        expense.setCategory(request.category());
        expense.setAmount(money(request.amount()));
        expense.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        expense.setShift(request.shiftId() == null ? null : ownedShift(email, request.shiftId()));
    }

    private Shift ownedShift(String email, Long id) {
        return shiftRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ShiftNotFoundException(id));
    }

    public ExpenseResponse toResponse(String email, Expense expense) {
        return toResponses(email, List.of(expense)).getFirst();
    }

    /** Responses for a list of expenses, their linked blocks read in one query, deleted blocks included. */
    public List<ExpenseResponse> toResponses(String email, List<Expense> expenses) {
        java.util.Map<Long, LinkedShift> links = linkedShifts(email, expenses);
        return expenses.stream().map(expense -> {
            LinkedShift link = linkOf(expense, links);
            return new ExpenseResponse(expense.getId(), expense.getDate(), expense.getCategory(), expense.getAmount(),
                    expense.getNote(), linkedShiftId(expense), link == null ? null : link.station(),
                    expense.getCreatedAt(), expense.getUpdatedAt(), expense.getDeletedAt(),
                    link != null && link.deleted());
        }).toList();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
