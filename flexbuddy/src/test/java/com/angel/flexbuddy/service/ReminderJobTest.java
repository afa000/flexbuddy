package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.PushMessage;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.ReminderKind;
import com.angel.flexbuddy.model.ReminderLog;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ReminderLogRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@ExtendWith(MockitoExtension.class)
class ReminderJobTest {

    private static final LocalDate SATURDAY = LocalDate.of(2026, 9, 12);

    @Mock ShiftRepository shiftRepository;
    @Mock ReminderLogRepository reminderLogRepository;
    @Mock PushService pushService;
    @Mock AppUserRepository userRepository;

    private AppUser owner;
    private Shift shift;

    @BeforeEach
    void setUp() {
        owner = new AppUser("Angel", "angel@example.com", "hash");
        owner.setTimeZone("America/Chicago");
        owner.setRemindBeforeMinutes(60);
        owner.setRemindConfirm(true);
        // 15:15 to 19:15 in Chicago is 20:15 to 00:15 UTC.
        shift = new Shift(7L, "VEA7", SATURDAY, LocalTime.of(15, 15), LocalTime.of(19, 15),
                new BigDecimal("84.00"), BigDecimal.ZERO, owner);
        shift.setStatus(ShiftStatus.SCHEDULED);
    }

    @Test
    void sendsAReminderInsideTheWindowOnlyOnce() {
        when(pushService.isConfigured()).thenReturn(true);
        when(shiftRepository.findScheduledWithLeadTime(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of(shift));
        when(reminderLogRepository.existsById(new ReminderLog.Key(7L, ReminderKind.BEFORE_START)))
                .thenReturn(false, true);
        ReminderJob job = job("2026-09-12T19:20:00Z");

        assertThat(job.sendShiftReminders()).isEqualTo(1);
        assertThat(job.sendShiftReminders()).isZero();

        ArgumentCaptor<PushMessage> message = ArgumentCaptor.forClass(PushMessage.class);
        verify(pushService, times(1)).send(eq(owner), message.capture());
        assertThat(message.getValue()).isEqualTo(new PushMessage("Upcoming block · VEA7",
                "Starts at 3:15 PM · $84.00 offered", "/?screen=schedule", "shift-7-start"));
        verify(reminderLogRepository, times(1)).save(any(ReminderLog.class));
    }

    @Test
    void skipsRemindersThatAreNotDueYetOrMoreThanFifteenMinutesLate() {
        when(pushService.isConfigured()).thenReturn(true);
        when(shiftRepository.findScheduledWithLeadTime(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of(shift));

        assertThat(job("2026-09-12T19:14:00Z").sendShiftReminders()).isZero();
        assertThat(job("2026-09-12T19:31:00Z").sendShiftReminders()).isZero();

        verify(pushService, never()).send(any(), any());
        verify(reminderLogRepository, never()).save(any());
    }

    @Test
    void theWindowIncludesNowButNotFifteenMinutesAgo() {
        Instant now = Instant.parse("2026-09-12T19:15:00Z");

        assertThat(ReminderJob.isDue(now, now)).isTrue();
        assertThat(ReminderJob.isDue(now.minusSeconds(899), now)).isTrue();
        assertThat(ReminderJob.isDue(now.minusSeconds(900), now)).isFalse();
        assertThat(ReminderJob.isDue(now.plusSeconds(1), now)).isFalse();
    }

    @Test
    void nudgesToConfirmABlockThatEndedBetweenOneAndTwentyFourHoursAgo() {
        when(pushService.isConfigured()).thenReturn(true);
        when(shiftRepository.findScheduledWithConfirmNudges(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 14)))
                .thenReturn(List.of(shift));
        when(reminderLogRepository.existsById(new ReminderLog.Key(7L, ReminderKind.CONFIRM))).thenReturn(false);

        assertThat(job("2026-09-13T00:45:00Z").sendConfirmationNudges()).isZero();
        assertThat(job("2026-09-13T02:00:00Z").sendConfirmationNudges()).isEqualTo(1);

        verify(pushService).send(owner, new PushMessage("Did you work this block?",
                "VEA7 · Sat Sep 12 15:15–19:15. Confirm it in FlexBuddy.", "/?screen=schedule", "shift-7-confirm"));
    }

    @Test
    void doesNothingWhenPushIsNotConfigured() {
        when(pushService.isConfigured()).thenReturn(false);
        ReminderJob job = job("2026-09-12T19:20:00Z");

        assertThat(job.sendShiftReminders()).isZero();
        assertThat(job.sendConfirmationNudges()).isZero();
        verifyNoInteractions(shiftRepository, reminderLogRepository);
    }

    private ReminderJob job(String instant) {
        Clock clock = Clock.fixed(Instant.parse(instant), ZoneOffset.UTC);
        return new ReminderJob(shiftRepository, reminderLogRepository, pushService,
                new UserTimeService(userRepository, clock), clock);
    }
}
