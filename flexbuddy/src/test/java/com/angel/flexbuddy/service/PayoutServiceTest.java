package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.dto.PayoutDepositRequest;
import com.angel.flexbuddy.exception.InvalidPayoutException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.PayoutDeposit;
import com.angel.flexbuddy.model.VehicleCostMethod;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.PayoutDepositRepository;

@ExtendWith(MockitoExtension.class)
class PayoutServiceTest {

    private static final String EMAIL = "angel@example.com";
    private static final Instant NOW = Instant.parse("2026-09-12T15:00:00Z");
    // Saturday; the default schedule pays on Tuesdays and Fridays.
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 12);
    private static final LocalDate FRIDAY = LocalDate.of(2026, 9, 11);

    @Mock PayoutDepositRepository deposits;
    @Mock AppUserRepository userRepository;
    @Mock AccountSettingsService settingsService;
    @Mock UserTimeService userTime;

    private PayoutService service;

    @BeforeEach
    void setUp() {
        service = new PayoutService(deposits, userRepository, settingsService, userTime, Clock.fixed(NOW, ZoneOffset.UTC));
        org.mockito.Mockito.lenient().when(userTime.today(EMAIL)).thenReturn(TODAY);
    }

    @Test
    void recordsWhatLandedForAPastPayoutDay() {
        AppUser owner = new AppUser("Angel", EMAIL, "hash");
        when(settingsService.get(EMAIL)).thenReturn(defaultSchedule());
        when(deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(EMAIL, FRIDAY)).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(owner));

        service.record(EMAIL, FRIDAY, new PayoutDepositRequest(new BigDecimal("84.5"), "  Chase deposit  "));

        ArgumentCaptor<PayoutDeposit> saved = ArgumentCaptor.forClass(PayoutDeposit.class);
        verify(deposits).save(saved.capture());
        assertThat(saved.getValue().getOwner()).isSameAs(owner);
        assertThat(saved.getValue().getPayoutDate()).isEqualTo(FRIDAY);
        assertThat(saved.getValue().getAmount()).isEqualByComparingTo("84.50");
        assertThat(saved.getValue().getAmount().scale()).isEqualTo(2);
        assertThat(saved.getValue().getNote()).isEqualTo("Chase deposit");
        assertThat(saved.getValue().getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    void recordingAgainReplacesTheAmountAndKeepsWhenItWasFirstRecorded() {
        PayoutDeposit existing = new PayoutDeposit();
        existing.setPayoutDate(FRIDAY);
        existing.setAmount(new BigDecimal("80.00"));
        existing.setNote("first look");
        existing.setCreatedAt(Instant.parse("2026-09-11T12:00:00Z"));
        when(settingsService.get(EMAIL)).thenReturn(defaultSchedule());
        when(deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(EMAIL, FRIDAY)).thenReturn(Optional.of(existing));

        service.record(EMAIL, FRIDAY, new PayoutDepositRequest(new BigDecimal("84.00"), " "));

        verify(deposits).save(existing);
        assertThat(existing.getAmount()).isEqualByComparingTo("84.00");
        assertThat(existing.getNote()).isNull();
        assertThat(existing.getCreatedAt()).isEqualTo(Instant.parse("2026-09-11T12:00:00Z"));
        assertThat(existing.getUpdatedAt()).isEqualTo(NOW);
        verify(userRepository, never()).findByEmailIgnoreCase(any());
    }

    @Test
    void aPayoutCannotBeRecordedBeforeItsDay() {
        assertThatThrownBy(() -> service.record(EMAIL, LocalDate.of(2026, 9, 15),
                new PayoutDepositRequest(new BigDecimal("84.00"), null)))
                .isInstanceOf(InvalidPayoutException.class)
                .hasMessage("A payout can be recorded once its day comes.");
        verify(deposits, never()).save(any());
    }

    @Test
    void onlyThePayoutDaysInTheScheduleCanBeRecorded() {
        when(settingsService.get(EMAIL)).thenReturn(defaultSchedule());

        assertThatThrownBy(() -> service.record(EMAIL, LocalDate.of(2026, 9, 10),
                new PayoutDepositRequest(new BigDecimal("84.00"), null)))
                .isInstanceOf(InvalidPayoutException.class)
                .hasMessage("That date is not one of your payout days.");
        verify(deposits, never()).save(any());
    }

    @Test
    void removingForgetsTheRecordedAmountAndIgnoresADayWithNone() {
        PayoutDeposit existing = new PayoutDeposit();
        when(deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(EMAIL, FRIDAY)).thenReturn(Optional.of(existing));
        when(deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(EMAIL, LocalDate.of(2026, 9, 8))).thenReturn(Optional.empty());

        service.remove(EMAIL, FRIDAY);
        service.remove(EMAIL, LocalDate.of(2026, 9, 8));

        verify(deposits).delete(existing);
    }

    private static AccountSettingsResponse defaultSchedule() {
        return new AccountSettingsResponse(VehicleCostMethod.STANDARD_MILEAGE, new BigDecimal("0.70"),
                new BigDecimal("0.70"), 2026);
    }
}
