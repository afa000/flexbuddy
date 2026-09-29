package com.angel.flexbuddy.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
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
import com.angel.flexbuddy.model.TaxReminderKind;
import com.angel.flexbuddy.model.TaxReminderLog;
import com.angel.flexbuddy.model.TimestampListener;

@DataJpaTest
@ActiveProfiles("test")
@Import({JpaAuditingConfig.class, TimestampListener.class})
class TaxReminderRepositoryTest {

    private static final Instant NOW = Instant.parse("2026-09-08T16:12:00Z");
    private static final LocalDate SEP_15 = LocalDate.of(2026, 9, 15);

    @Autowired AppUserRepository userRepository;
    @Autowired TaxReminderLogRepository logRepository;
    @MockitoBean Clock clock;

    @BeforeEach
    void fixTheClock() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @Test
    void findByRemindTaxTrueReturnsOnlyOptedInAccounts() {
        AppUser optedIn = new AppUser("Angel", "angel@example.com", "hash");
        optedIn.setRemindTax(true);
        userRepository.save(optedIn);
        userRepository.save(new AppUser("Other", "other@example.com", "hash"));

        assertThat(userRepository.findByRemindTaxTrue()).extracting(AppUser::getEmail).containsExactly("angel@example.com");
    }

    @Test
    void theTaxReminderLogIsKeyedByOwnerDateAndKind() {
        AppUser owner = userRepository.save(new AppUser("Angel", "angel@example.com", "hash"));
        logRepository.saveAndFlush(new TaxReminderLog(owner.getId(), SEP_15, TaxReminderKind.WEEK_BEFORE, NOW));

        assertThat(logRepository.existsById(new TaxReminderLog.Key(owner.getId(), SEP_15, TaxReminderKind.WEEK_BEFORE))).isTrue();
        assertThat(logRepository.existsById(new TaxReminderLog.Key(owner.getId(), SEP_15, TaxReminderKind.DUE_DAY))).isFalse();
        assertThat(logRepository.existsById(new TaxReminderLog.Key(owner.getId() + 1, SEP_15, TaxReminderKind.WEEK_BEFORE))).isFalse();
        assertThat(logRepository.deleteAllByOwnerId(owner.getId())).isEqualTo(1);
    }
}
