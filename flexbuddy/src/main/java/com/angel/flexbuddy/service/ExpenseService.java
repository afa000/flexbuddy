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
        return findFiltered(email, filter).stream().map(this::toResponse).toList();
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
        return expenseRepository.findAllByShiftIdAndOwnerEmailIgnoreCaseOrderByDateDesc(shiftId, email)
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public ExpenseResponse create(String email, ExpenseRequest request) {
        AppUser owner = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));
        Expense expense = new Expense();
        expense.setOwner(owner);
        apply(expense, email, request);
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse update(String email, Long id, ExpenseRequest request) {
        Expense expense = expenseRepository.findByIdAndOwnerEmailIgnoreCase(id, email)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
        apply(expense, email, request);
        return toResponse(expenseRepository.save(expense));
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
        return expenseRepository.findByIdAndOwnerEmailIgnoreCase(id, email).map(this::toResponse)
                .orElseThrow(() -> new ExpenseNotFoundException(id));
    }

    @Transactional
    public int restoreBatch(String email, String batch) { return expenseRepository.restoreBatch(email, batch); }

    @Transactional
    public List<ExpenseResponse> getTrash(String email) {
        expenseRepository.purgeDeletedBefore(Instant.now(clock).minus(30, java.time.temporal.ChronoUnit.DAYS));
        return expenseRepository.findTrash(email).stream().map(this::toResponse).toList();
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

    public ExpenseResponse toResponse(Expense expense) {
        Shift shift = expense.getShift();
        return new ExpenseResponse(expense.getId(), expense.getDate(), expense.getCategory(), expense.getAmount(),
                expense.getNote(), shift == null ? null : shift.getId(), shift == null ? null : shift.getStation(),
                expense.getCreatedAt(), expense.getUpdatedAt(), expense.getDeletedAt());
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }
}
