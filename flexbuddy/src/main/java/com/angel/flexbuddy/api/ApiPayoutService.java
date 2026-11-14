package com.angel.flexbuddy.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.PayoutDepositRequest;
import com.angel.flexbuddy.model.PayoutDeposit;
import com.angel.flexbuddy.repository.PayoutDepositRepository;
import com.angel.flexbuddy.service.PayoutService;
import com.angel.flexbuddy.service.ShiftReportService;

/**
 * Pay periods and recorded payouts for the native app (NA-06). Figures come from the website's services; writes go
 * through PayoutService with the outbox's key and version rules.
 */
@Service
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiPayoutService {
    public enum Action { PAYOUT_RECORD, PAYOUT_REMOVE }

    /** The body of a payout record; {@code expectedUpdatedAt} is null when the phone saw nothing recorded. */
    public record Input(BigDecimal amount, String note, Instant expectedUpdatedAt) { }

    private final ShiftReportService reports;
    private final PayoutDepositRepository deposits;
    private final PayoutService service;
    private final ApiWriteReceipts receipts;

    public ApiPayoutService(ShiftReportService reports, PayoutDepositRepository deposits, PayoutService service,
            ApiWriteReceipts receipts) {
        this.reports = reports;
        this.deposits = deposits;
        this.service = service;
        this.receipts = receipts;
    }

    /**
     * One snapshot for both reads, so a payout recorded on the website in between can't pair the old amount with the
     * new version (which would let the phone's next edit overwrite an amount the driver never saw).
     */
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public ApiPayouts payouts(String email, int count) {
        var periods = reports.payPeriods(email, count);
        Map<LocalDate, Instant> versions = new HashMap<>();
        for (PayoutDeposit deposit : deposits.findByOwnerEmailIgnoreCaseOrderByPayoutDateAsc(email)) {
            versions.put(deposit.getPayoutDate(), deposit.getUpdatedAt());
        }
        return ApiPayouts.of(periods, versions);
    }

    /** Records or removes what landed for one payout date. A retry acknowledges without applying it again. */
    @Transactional
    public void write(String email, Action action, LocalDate payoutDate, String header, Input input,
            Instant expectedUpdatedAt) {
        String key = receipts.key(header);
        var owner = receipts.lock(email);
        String hash = receipts.hash(action.name(), payoutDate, input, expectedUpdatedAt);
        if (receipts.previous(owner, key, hash).isPresent()) return;
        PayoutDeposit current = deposits.lockByOwnerAndDate(email, payoutDate).orElse(null);
        if (!ApiWriteReceipts.sameVersion(current == null ? null : current.getUpdatedAt(), expectedUpdatedAt)) {
            throw new ApiWriteReceipts.Stale(ApiPayouts.Deposit.of(current));
        }
        switch (action) {
            case PAYOUT_RECORD -> service.record(email, payoutDate,
                    receipts.valid(new PayoutDepositRequest(input.amount(), input.note())));
            case PAYOUT_REMOVE -> service.remove(email, payoutDate);
        }
        receipts.save(owner, key, hash, receipt -> receipt.setPayoutDate(payoutDate));
    }
}
