package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.ExpenseFilter;
import com.angel.flexbuddy.dto.ExpenseRequest;
import com.angel.flexbuddy.exception.ExpenseNotFoundException;
import com.angel.flexbuddy.exception.ShiftNotFoundException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@ExtendWith(MockitoExtension.class)
class ExpenseServiceTest {
    private static final String EMAIL = "angel@example.com";
    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");

    @Mock ExpenseRepository expenseRepository;
    @Mock ShiftRepository shiftRepository;
    @Mock AppUserRepository userRepository;

    private ExpenseService service;
    private AppUser owner;
    private Shift shift;

    @BeforeEach
    void setUp() {
        service = new ExpenseService(expenseRepository, shiftRepository, userRepository,
                Clock.fixed(NOW, ZoneOffset.UTC));
        owner = new AppUser("Angel", EMAIL, "hash");
        owner.setId(1L);
        shift = new Shift(8L, "VEA7", LocalDate.of(2026, 9, 12), LocalTime.of(9, 0),
                LocalTime.of(13, 0), new BigDecimal("100.00"), BigDecimal.ZERO, owner);
    }

    @Test
    void createLinksOnlyAnOwnedShiftAndNormalizesTheAmountAndNote() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(owner));
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(8L, EMAIL)).thenReturn(Optional.of(shift));
        when(expenseRepository.save(any(Expense.class))).thenAnswer(invocation -> {
            Expense expense = invocation.getArgument(0);
            expense.setId(20L);
            return expense;
        });

        var result = service.create(EMAIL, new ExpenseRequest(LocalDate.of(2026, 9, 12),
                ExpenseCategory.TOLL, new BigDecimal("4.5"), "  bridge  ", 8L));

        assertThat(result.id()).isEqualTo(20L);
        assertThat(result.amount()).isEqualByComparingTo("4.50");
        assertThat(result.note()).isEqualTo("bridge");
        assertThat(result.shiftId()).isEqualTo(8L);
        assertThat(result.station()).isEqualTo("VEA7");
    }

    @Test
    void createRejectsAShiftThatDoesNotBelongToTheUser() {
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(owner));
        when(shiftRepository.findByIdAndOwnerEmailIgnoreCase(99L, EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(EMAIL, new ExpenseRequest(LocalDate.now(),
                ExpenseCategory.FUEL, new BigDecimal("35.00"), null, 99L)))
                .isInstanceOf(ShiftNotFoundException.class);
    }

    @Test
    void summaryTotalsEveryCategoryAndReturnsZeroForMissingCategories() {
        when(expenseRepository.findFiltered(EMAIL, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "", "", null, null)).thenReturn(List.of(
                        expense(ExpenseCategory.FUEL, "40.00"),
                        expense(ExpenseCategory.TOLL, "5.25")));

        var result = service.summary(EMAIL, new ExpenseFilter(LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31), null, null, null, null));

        assertThat(result.total()).isEqualByComparingTo("45.25");
        assertThat(result.count()).isEqualTo(2);
        assertThat(result.byCategory().get(ExpenseCategory.FUEL)).isEqualByComparingTo("40.00");
        assertThat(result.byCategory().get(ExpenseCategory.MAINTENANCE)).isEqualByComparingTo("0.00");
    }

    @Test
    void deleteIsOwnerScopedAndReturnsTheUndoBatch() {
        when(expenseRepository.softDelete(org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.eq(20L), org.mockito.ArgumentMatchers.eq(NOW), any(String.class)))
                .thenReturn(1);

        String batch = service.delete(EMAIL, 20L);

        assertThat(batch).isNotBlank();
        verify(expenseRepository).softDelete(EMAIL, 20L, NOW, batch);
    }

    @Test
    void deleteReportsMissingOrForeignExpensesAsNotFound() {
        when(expenseRepository.softDelete(org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.eq(20L), org.mockito.ArgumentMatchers.eq(NOW), any(String.class)))
                .thenReturn(0);

        assertThatThrownBy(() -> service.delete(EMAIL, 20L)).isInstanceOf(ExpenseNotFoundException.class);
    }

    private Expense expense(ExpenseCategory category, String amount) {
        Expense expense = new Expense();
        expense.setOwner(owner);
        expense.setDate(LocalDate.of(2026, 9, 12));
        expense.setCategory(category);
        expense.setAmount(new BigDecimal(amount));
        return expense;
    }
}
