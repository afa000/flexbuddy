package com.angel.flexbuddy.api;

import java.util.List;

import com.angel.flexbuddy.dto.TaxQuarterResponse;
import com.angel.flexbuddy.dto.TaxSummaryResponse;

/** The annual tax figures for the native app, with payments represented by totals. */
record ApiTaxSummary(int year, String percent, String netYearToDate, String reserveToDate, String paid,
        String remaining, String thisWeekNet, String thisWeekSetAside, String nextDueDate, List<Quarter> quarters) {

    static ApiTaxSummary of(TaxSummaryResponse summary) {
        return new ApiTaxSummary(summary.year(), ApiValues.decimal(summary.percent()),
                ApiValues.money(summary.netYearToDate()), ApiValues.money(summary.reserveToDate()),
                ApiValues.money(summary.paid()), ApiValues.money(summary.remaining()), ApiValues.money(summary.thisWeekNet()),
                ApiValues.money(summary.thisWeekSetAside()), ApiValues.date(summary.nextDueDate()),
                summary.quarters().stream().map(Quarter::of).toList());
    }

    record Quarter(int quarter, String from, String to, String dueDate, String net, String setAside, String paid) {
        static Quarter of(TaxQuarterResponse quarter) {
            return new Quarter(quarter.quarter(), ApiValues.date(quarter.from()), ApiValues.date(quarter.to()),
                    ApiValues.date(quarter.dueDate()), ApiValues.money(quarter.net()), ApiValues.money(quarter.setAside()),
                    ApiValues.money(quarter.paid()));
        }
    }
}
