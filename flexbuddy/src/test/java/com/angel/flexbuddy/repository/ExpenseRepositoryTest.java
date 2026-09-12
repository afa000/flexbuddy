package com.angel.flexbuddy.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.angel.flexbuddy.config.JpaAuditingConfig;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.TimestampListener;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, TimestampListener.class})
class ExpenseRepositoryTest {
    @Autowired ExpenseRepository expenseRepository;
    @Autowired ShiftRepository shiftRepository;
    @Autowired AppUserRepository userRepository;
    @Autowired EntityManager entityManager;
    @MockitoBean Clock clock;

    @BeforeEach void clock() {
        when(clock.instant()).thenReturn(Instant.parse("2026-09-12T12:00:00Z"));
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void filtersByOwnerAndLinkedStationAndSupportsSoftDeleteRecovery() {
        AppUser angel = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        AppUser other = userRepository.save(new AppUser("Other", "other@example.com", "hash"));
        Shift shift = shiftRepository.save(shift(angel, "VEA7"));
        Expense visible = expenseRepository.save(expense(angel, shift, "12.50"));
        expenseRepository.save(expense(other, null, "99.00"));
        entityManager.flush();

        assertThat(expenseRepository.findFiltered("angel@example.com", LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                "VEA7", "gas", ExpenseCategory.FUEL, null)).extracting(Expense::getId)
                .containsExactly(visible.getId());

        expenseRepository.softDelete("angel@example.com", visible.getId(), Instant.now(clock), "batch");
        entityManager.clear();
        assertThat(expenseRepository.findByIdAndOwnerEmailIgnoreCase(visible.getId(), "angel@example.com")).isEmpty();
        assertThat(expenseRepository.findTrash("angel@example.com")).hasSize(1);
        assertThat(expenseRepository.restoreBatch("angel@example.com", "batch")).isEqualTo(1);
    }

    private Shift shift(AppUser owner, String station) {
        return new Shift(null, station, LocalDate.of(2026, 9, 10), LocalTime.of(8,0), LocalTime.of(12,0),
                new BigDecimal("100"), BigDecimal.ZERO, owner);
    }
    private Expense expense(AppUser owner, Shift shift, String amount) {
        Expense expense = new Expense(); expense.setOwner(owner); expense.setShift(shift);
        expense.setDate(LocalDate.of(2026, 9, 10)); expense.setCategory(ExpenseCategory.FUEL);
        expense.setAmount(new BigDecimal(amount)); expense.setNote("Gas receipt"); return expense;
    }
}
