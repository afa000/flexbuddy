package com.angel.flexbuddy.api;

import static com.angel.flexbuddy.api.ApiValues.code;
import static com.angel.flexbuddy.api.ApiValues.date;
import static com.angel.flexbuddy.api.ApiValues.instant;
import static com.angel.flexbuddy.api.ApiValues.money;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.angel.flexbuddy.dto.PayPeriodResponse;
import com.angel.flexbuddy.dto.PayPeriodsResponse;
import com.angel.flexbuddy.model.PayoutDeposit;

/**
 * The website's pay periods as the API returns them (NA-06), each with the recorded deposit's {@code updatedAt} (null
 * when nothing is recorded), which is the version a payout write sends back.
 */
public record ApiPayouts(Period nextPayout, List<Period> periods) {
    public record Period(
            String payoutDate,
            String from,
            String to,
            int blocks,
            String earned,
            int scheduledBlocks,
            String scheduledPay,
            String received,
            String difference,
            String note,
            String status,
            String updatedAt
    ) {
        static Period of(PayPeriodResponse period, Map<LocalDate, Instant> versions) {
            if (period == null) return null;
            return new Period(date(period.payoutDate()), date(period.from()), date(period.to()), period.blocks(),
                    money(period.earned()), period.scheduledBlocks(), money(period.scheduledPay()),
                    money(period.received()), money(period.difference()), period.note(), code(period.status()),
                    instant(versions.get(period.payoutDate())));
        }
    }

    /** A recorded payout, as a conflict answer's {@code current}. */
    public record Deposit(String payoutDate, String amount, String note, String updatedAt) {
        static Deposit of(PayoutDeposit deposit) {
            return deposit == null ? null : new Deposit(date(deposit.getPayoutDate()), money(deposit.getAmount()),
                    deposit.getNote(), instant(deposit.getUpdatedAt()));
        }
    }

    static ApiPayouts of(PayPeriodsResponse response, Map<LocalDate, Instant> versions) {
        return new ApiPayouts(Period.of(response.nextPayout(), versions),
                response.periods().stream().map(period -> Period.of(period, versions)).toList());
    }
}
