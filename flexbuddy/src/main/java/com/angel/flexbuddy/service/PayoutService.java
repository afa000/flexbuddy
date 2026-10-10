package com.angel.flexbuddy.service;

import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.PayoutDepositRequest;
import com.angel.flexbuddy.exception.InvalidPayoutException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.PayoutDeposit;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.PayoutDepositRepository;

/**
 * Records what actually landed for a payout, so the pay-period list can compare it with what the blocks earned. One
 * amount per payout date: recording again replaces it.
 */
@Service
public class PayoutService {

    private final PayoutDepositRepository deposits;
    private final AppUserRepository userRepository;
    private final AccountSettingsService settingsService;
    private final UserTimeService userTime;
    private final Clock clock;

    public PayoutService(PayoutDepositRepository deposits, AppUserRepository userRepository,
            AccountSettingsService settingsService, UserTimeService userTime, Clock clock) {
        this.deposits = deposits;
        this.userRepository = userRepository;
        this.settingsService = settingsService;
        this.userTime = userTime;
        this.clock = clock;
    }

    @Transactional
    public void record(String email, LocalDate payoutDate, PayoutDepositRequest request) {
        if (payoutDate.isAfter(userTime.today(email))) {
            throw new InvalidPayoutException("error.payout.notYet");
        }
        if (!settingsService.get(email).payoutDays().contains(payoutDate.getDayOfWeek())) {
            throw new InvalidPayoutException("error.payout.notPayoutDay");
        }
        PayoutDeposit deposit = deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(email, payoutDate).orElseGet(() -> {
            AppUser owner = userRepository.findByEmailIgnoreCase(email)
                    .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));
            PayoutDeposit created = new PayoutDeposit();
            created.setOwner(owner);
            created.setPayoutDate(payoutDate);
            created.setCreatedAt(Instant.now(clock));
            return created;
        });
        deposit.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        deposit.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        deposit.setUpdatedAt(Instant.now(clock));
        deposits.save(deposit);
    }

    /** Forgets what was recorded for a payout, which then shows as unchecked again. Nothing recorded is not an error. */
    @Transactional
    public void remove(String email, LocalDate payoutDate) {
        deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(email, payoutDate).ifPresent(deposits::delete);
    }
}
