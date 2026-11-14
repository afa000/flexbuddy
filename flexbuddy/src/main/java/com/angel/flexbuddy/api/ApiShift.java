package com.angel.flexbuddy.api;

import static com.angel.flexbuddy.api.ApiValues.code;
import static com.angel.flexbuddy.api.ApiValues.decimal;
import static com.angel.flexbuddy.api.ApiValues.instant;
import static com.angel.flexbuddy.api.ApiValues.money;
import static com.angel.flexbuddy.api.ApiValues.time;

import com.angel.flexbuddy.dto.BlockDetailsResponse;
import com.angel.flexbuddy.dto.ShiftResponse;

/**
 * A block as the API returns it. Only figures that depend on the block alone are included: a delta sync sends a block
 * again only when the block changes, so figures that also depend on expenses or settings (vehicle cost, linked
 * expenses, net, the forfeit deadline) would go stale on the phone. Home carries the net totals.
 */
public record ApiShift(
        long id,
        String station,
        String date,
        String startTime,
        String endTime,
        String status,
        String statusChangedAt,
        boolean lateForfeit,
        String basePay,
        String tips,
        String totalPay,
        String earnedPay,
        int timeWorked,
        String hourlyRate,
        String miles,
        String earningsPerMile,
        Details details,
        String createdAt,
        String updatedAt
) {
    public record Details(
            String actualStart,
            String actualEnd,
            Integer actualMinutes,
            String actualHourlyRate,
            Integer finishedEarlyMinutes,
            String odometerStart,
            String odometerEnd,
            Integer stops,
            Integer packages,
            Integer returns,
            String minutesPerStop,
            String returnsRate
    ) {
        static Details of(BlockDetailsResponse details) {
            if (details == null) details = BlockDetailsResponse.NONE;
            return new Details(time(details.actualStart()), time(details.actualEnd()), details.actualMinutes(),
                    money(details.actualHourlyRate()), details.finishedEarlyMinutes(),
                    decimal(details.odometerStart()), decimal(details.odometerEnd()), details.stops(),
                    details.packages(), details.returns(), decimal(details.minutesPerStop()),
                    decimal(details.returnsRate()));
        }
    }

    static ApiShift of(ShiftResponse shift) {
        return new ApiShift(shift.getId(), shift.getStation(), ApiValues.date(shift.getDate()),
                time(shift.getStartTime()), time(shift.getEndTime()), code(shift.getStatus()), instant(shift.getStatusChangedAt()),
                shift.isLateForfeit(), money(shift.getBasePay()), money(shift.getTips()), money(shift.getTotalPay()),
                money(shift.getEarnedPay()), shift.getTimeWorked(), money(shift.getHourlyRate()),
                decimal(shift.getMiles()), money(shift.getEarningsPerMile()), Details.of(shift.getDetails()),
                instant(shift.getCreatedAt()), instant(shift.getUpdatedAt()));
    }
}
