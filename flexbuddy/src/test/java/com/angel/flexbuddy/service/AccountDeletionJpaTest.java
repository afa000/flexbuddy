package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.angel.flexbuddy.config.JpaAuditingConfig;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.PushSubscription;
import com.angel.flexbuddy.model.ReminderKind;
import com.angel.flexbuddy.model.ReminderLog;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.TimestampListener;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;
import com.angel.flexbuddy.repository.PushSubscriptionRepository;
import com.angel.flexbuddy.repository.ReminderLogRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

import jakarta.persistence.EntityManager;

@DataJpaTest
@ActiveProfiles("test")
@Import({AccountService.class, JpaAuditingConfig.class, TimestampListener.class})
class AccountDeletionJpaTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");

    @Autowired
    private AccountService accountService;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private ExpenseRepository expenseRepository;

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Autowired
    private ReminderLogRepository reminderLogRepository;

    @Autowired
    private com.angel.flexbuddy.repository.StandingEntryRepository standingEntryRepository;

    @Autowired
    private com.angel.flexbuddy.repository.TaxReminderLogRepository taxReminderLogRepository;

    @Autowired
    private com.angel.flexbuddy.repository.PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private com.angel.flexbuddy.repository.EmailCodeRepository emailCodeRepository;

    @Autowired
    private com.angel.flexbuddy.repository.RecoveryCodeRepository recoveryCodeRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private Clock clock;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private PersistentTokenRepository persistentTokenRepository;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void deleteAccountLeavesNoOwnedOrOrphanRowsIncludingTrash() {
        AppUser owner = new AppUser("Angel", "angel@example.com", "stored-hash");
        // A driver who dismissed the setup card is deleted like any other.
        owner.setSetupDismissedAt(NOW);
        owner = userRepository.save(owner);
        Shift activeShift = shiftRepository.save(shift(owner, "VEA7"));
        Shift trashedShift = shift(owner, "DOB2");
        trashedShift.setDeletedAt(NOW.minusSeconds(60));
        shiftRepository.save(trashedShift);

        expenseRepository.save(expense(owner, activeShift, null));
        Expense trashedExpense = expense(owner, trashedShift, NOW.minusSeconds(60));
        expenseRepository.save(trashedExpense);

        PushSubscription subscription = new PushSubscription();
        subscription.setOwner(owner);
        subscription.setEndpoint("https://push.example/subscription");
        subscription.setP256dh("key");
        subscription.setAuth("auth");
        subscription.setCreatedAt(NOW);
        pushSubscriptionRepository.save(subscription);

        reminderLogRepository.save(new ReminderLog(activeShift.getId(), ReminderKind.BEFORE_START, NOW));
        com.angel.flexbuddy.model.StandingEntry standing = new com.angel.flexbuddy.model.StandingEntry();
        standing.setOwner(owner);
        standing.setRecordedOn(LocalDate.of(2026, 9, 10));
        standing.setLevel(com.angel.flexbuddy.model.StandingLevel.FAIR);
        standing.setCreatedAt(NOW);
        standing.setUpdatedAt(NOW);
        standingEntryRepository.save(standing);
        taxReminderLogRepository.save(new com.angel.flexbuddy.model.TaxReminderLog(owner.getId(),
                LocalDate.of(2026, 9, 15), com.angel.flexbuddy.model.TaxReminderKind.WEEK_BEFORE, NOW));
        com.angel.flexbuddy.model.PasswordResetToken resetLink = new com.angel.flexbuddy.model.PasswordResetToken();
        resetLink.setOwner(owner);
        resetLink.setTokenHash("a".repeat(64));
        resetLink.setCreatedAt(NOW);
        resetLink.setExpiresAt(NOW.plusSeconds(1800));
        passwordResetTokenRepository.save(resetLink);
        com.angel.flexbuddy.model.EmailCode code = new com.angel.flexbuddy.model.EmailCode();
        code.setOwner(owner);
        code.setPurpose(com.angel.flexbuddy.model.EmailCodePurpose.VERIFY_EMAIL);
        code.setCodeHash("b".repeat(64));
        code.setCreatedAt(NOW);
        code.setExpiresAt(NOW.plusSeconds(900));
        emailCodeRepository.save(code);
        com.angel.flexbuddy.model.RecoveryCode recovery = new com.angel.flexbuddy.model.RecoveryCode();
        recovery.setOwner(owner);
        recovery.setCodeHash("c".repeat(64));
        recovery.setCreatedAt(NOW);
        recoveryCodeRepository.save(recovery);
        entityManager.flush();
        when(passwordEncoder.matches("correct-password", "stored-hash")).thenReturn(true);

        accountService.deleteAccount("angel@example.com", "correct-password");
        entityManager.clear();

        assertThat(rowCount("app_users")).isZero();
        assertThat(rowCount("shift")).isZero();
        assertThat(rowCount("expense")).isZero();
        assertThat(rowCount("push_subscription")).isZero();
        assertThat(rowCount("reminder_log")).isZero();
        assertThat(rowCount("standing_entry")).isZero();
        assertThat(rowCount("tax_reminder_log")).isZero();
        assertThat(rowCount("password_reset_token")).isZero();
        assertThat(rowCount("email_code")).isZero();
        assertThat(rowCount("recovery_code")).isZero();
        verify(persistentTokenRepository).removeUserTokens("angel@example.com");
    }

    private Shift shift(AppUser owner, String station) {
        return new Shift(null, station, LocalDate.of(2026, 9, 13), LocalTime.of(9, 0),
                LocalTime.of(13, 0), new BigDecimal("100.00"), BigDecimal.ZERO, owner);
    }

    private Expense expense(AppUser owner, Shift shift, Instant deletedAt) {
        Expense expense = new Expense();
        expense.setOwner(owner);
        expense.setShift(shift);
        expense.setDate(LocalDate.of(2026, 9, 13));
        expense.setCategory(ExpenseCategory.FUEL);
        expense.setAmount(new BigDecimal("20.00"));
        expense.setNote("Fuel");
        expense.setDeletedAt(deletedAt);
        return expense;
    }

    private int rowCount(String table) {
        return jdbcTemplate.queryForObject("select count(*) from " + table, Integer.class);
    }
}
