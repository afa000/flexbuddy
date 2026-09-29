package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.PushMessage;
import com.angel.flexbuddy.dto.TaxQuarterResponse;
import com.angel.flexbuddy.dto.TaxSummaryResponse;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.TaxReminderKind;
import com.angel.flexbuddy.model.TaxReminderLog;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.TaxReminderLogRepository;

@ExtendWith(MockitoExtension.class)
class TaxReminderJobTest {

    private static final String EMAIL = "angel@example.com";
    private static final LocalDate SEP_15 = LocalDate.of(2026, 9, 15);

    @Mock AppUserRepository userRepository;
    @Mock TaxReminderLogRepository logRepository;
    @Mock TaxService taxService;
    @Mock PushService pushService;

    private AppUser owner;
    private BigDecimal q3SetAside = new BigDecimal("412.50");

    @BeforeEach
    void setUp() {
        owner = new AppUser("Angel", EMAIL, "hash");
        owner.setId(1L);
        owner.setTimeZone("America/Los_Angeles");
        owner.setRemindTax(true);
        lenient().when(userRepository.findByRemindTaxTrue()).thenReturn(List.of(owner));
        lenient().when(pushService.isConfigured()).thenReturn(true);
        // The configured dates: April 15, June 15, September 15, and January 15 of the following year.
        lenient().when(taxService.dueDate(anyInt(), anyInt())).thenAnswer(invocation -> {
            int year = invocation.getArgument(0);
            return switch ((int) invocation.getArgument(1)) {
                case 1 -> LocalDate.of(year, 4, 15);
                case 2 -> LocalDate.of(year, 6, 15);
                case 3 -> LocalDate.of(year, 9, 15);
                default -> LocalDate.of(year + 1, 1, 15);
            };
        });
        lenient().when(taxService.summary(eq(EMAIL), anyInt())).thenAnswer(invocation -> summary(invocation.getArgument(1)));
    }

    private TaxSummaryResponse summary(int year) {
        List<TaxQuarterResponse> quarters = List.of(
                quarter(1, year, 4, 15, BigDecimal.ZERO, BigDecimal.ZERO),
                quarter(2, year, 6, 15, BigDecimal.ZERO, BigDecimal.ZERO),
                quarter(3, year, 9, 15, q3SetAside, q3SetAside == null ? null : new BigDecimal("300.00")),
                quarter(4, year + 1, 1, 15, BigDecimal.ZERO, BigDecimal.ZERO));
        return new TaxSummaryResponse(year, q3SetAside == null ? null : new BigDecimal("25"), BigDecimal.ZERO, null,
                BigDecimal.ZERO, null, BigDecimal.ZERO, null, null, quarters, List.of());
    }

    private TaxQuarterResponse quarter(int number, int dueYear, int month, int day, BigDecimal setAside, BigDecimal paid) {
        return new TaxQuarterResponse(number, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31),
                LocalDate.of(dueYear, month, day), BigDecimal.ZERO, q3SetAside == null && number == 3 ? null : setAside,
                paid == null ? BigDecimal.ZERO : paid);
    }

    private TaxReminderJob job(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new TaxReminderJob(userRepository, logRepository, taxService, pushService,
                new UserTimeService(userRepository, clock), clock);
    }

    @Test
    void sendsTheWeekBeforeReminderAtNineLocalOnce() {
        when(logRepository.existsById(new TaxReminderLog.Key(1L, SEP_15, TaxReminderKind.WEEK_BEFORE)))
                .thenReturn(false, true);

        // 08:12 in Los Angeles is too early.
        assertThat(job("2026-09-08T15:12:00Z").sendTaxReminders()).isZero();
        verify(pushService, never()).send(any(), any());

        assertThat(job("2026-09-08T16:12:00Z").sendTaxReminders()).isEqualTo(1);
        assertThat(job("2026-09-08T17:12:00Z").sendTaxReminders()).isZero();

        verify(pushService, times(1)).send(owner, new PushMessage("Estimated tax due Tue Sep 15",
                "Q3 estimate to set aside: $412.50 · $300.00 recorded as paid. An estimate from your own numbers, not tax advice.",
                "/account#taxes", "tax-2026-q3-week"));
        verify(logRepository, times(1)).save(any(TaxReminderLog.class));
    }

    @Test
    void sendsTheDueDayReminderOnTheDay() {
        assertThat(job("2026-09-15T16:12:00Z").sendTaxReminders()).isEqualTo(1);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).send(eq(owner), message.capture());
        assertThat(message.getValue().title()).isEqualTo("Estimated tax due today · Q3");
        assertThat(message.getValue().tag()).isEqualTo("tax-2026-q3-due");
        ArgumentCaptor<TaxReminderLog> logged = ArgumentCaptor.forClass(TaxReminderLog.class);
        verify(logRepository).save(logged.capture());
        assertThat(logged.getValue().getKind()).isEqualTo(TaxReminderKind.DUE_DAY);
        assertThat(logged.getValue().getDueDate()).isEqualTo(SEP_15);
        assertThat(logged.getValue().getSentAt()).isEqualTo(Instant.parse("2026-09-15T16:12:00Z"));
    }

    @Test
    void januaryDueDateUsesTheFourthQuarterOfThePreviousYear() {
        // 09:12 in Los Angeles on January 8, a week before January 15 of the following year.
        assertThat(job("2027-01-08T17:12:00Z").sendTaxReminders()).isEqualTo(1);

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).send(eq(owner), message.capture());
        assertThat(message.getValue().title()).isEqualTo("Estimated tax due Fri Jan 15");
        assertThat(message.getValue().tag()).isEqualTo("tax-2026-q4-week");
        assertThat(message.getValue().body()).startsWith("Q4 estimate to set aside:");
        verify(taxService).summary(EMAIL, 2026);
    }

    @Test
    void withoutAPercentageTheBodyAsksForOne() {
        q3SetAside = null;

        job("2026-09-15T16:12:00Z").sendTaxReminders();

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService).send(eq(owner), message.capture());
        assertThat(message.getValue().body())
                .isEqualTo("Q3 payment is due. Choose a set-aside percentage in FlexBuddy to see an estimate.");
    }

    @Test
    void usesTheAccountTimeZoneForTheDayAndHour() {
        AppUser tokyo = new AppUser("Kenji", "kenji@example.com", "hash");
        tokyo.setId(2L);
        tokyo.setTimeZone("Asia/Tokyo");
        tokyo.setRemindTax(true);
        lenient().when(userRepository.findByRemindTaxTrue()).thenReturn(List.of(owner, tokyo));
        lenient().when(taxService.summary(eq("kenji@example.com"), anyInt())).thenAnswer(invocation -> summary(invocation.getArgument(1)));

        // 09:12 on September 8 in Tokyo is 17:12 on September 7 in Los Angeles.
        assertThat(job("2026-09-08T00:12:00Z").sendTaxReminders()).isEqualTo(1);

        verify(pushService).send(eq(tokyo), any(PushMessage.class));
        verify(pushService, never()).send(eq(owner), any(PushMessage.class));
    }

    @Test
    void nothingOnOtherDays() {
        assertThat(job("2026-09-10T18:12:00Z").sendTaxReminders()).isZero();

        verify(pushService, never()).send(any(), any());
        verify(logRepository, never()).save(any());
    }

    @Test
    void doesNothingWhenPushIsNotConfigured() {
        when(pushService.isConfigured()).thenReturn(false);

        assertThat(job("2026-09-15T16:12:00Z").sendTaxReminders()).isZero();

        verifyNoInteractions(userRepository, logRepository, taxService);
    }
}
