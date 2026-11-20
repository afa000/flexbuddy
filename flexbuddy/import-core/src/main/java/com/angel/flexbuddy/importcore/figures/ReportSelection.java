package com.angel.flexbuddy.importcore.figures;

import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;

/**
 * Which of the signed-in account's live blocks and expenses a report counts, for a consumer that filters its own
 * cache. These restate the website's database selection over plain values, so both pick the same rows: an inclusive
 * date range (either end open), a station, and for blocks a set of statuses. The website's text search is not
 * included. Rows with no date are never selected.
 *
 * <p>The station filter is trimmed, and a blank one means every station. It is compared case-insensitively with the
 * station as stored, which is not trimmed: a block saved as {@code " VEA7 "} does not match {@code VEA7}. An expense
 * matches a station only through a live linked block; one that is unlinked or whose block is deleted matches only
 * when no station is given.
 */
public final class ReportSelection {

    /** The statuses a report counts when none are given: the ones that can carry earnings. */
    public static final Set<BlockStatus> EARNINGS_STATUSES =
            Collections.unmodifiableSet(EnumSet.of(BlockStatus.COMPLETED, BlockStatus.CANCELLED));

    private ReportSelection() {
    }

    /** Whether a block is in the report. Null or empty {@code statuses} means {@link #EARNINGS_STATUSES}. */
    public static boolean blockMatches(ReportBlock block, LocalDate from, LocalDate to, String station,
            Set<BlockStatus> statuses) {
        Block plain = block.block();
        Set<BlockStatus> wanted = statuses == null || statuses.isEmpty() ? EARNINGS_STATUSES : statuses;
        return inRange(plain.date(), from, to) && wanted.contains(EarningsRules.effective(plain.status()))
                && stationMatches(plain.station(), station);
    }

    /** Whether an expense is in the report: in the range, on the station's live block if one is given, not waiting. */
    public static boolean costMatches(ReportCost cost, LocalDate from, LocalDate to, String station) {
        if (!inRange(cost.cost().date(), from, to) || cost.waits()) return false;
        if (normalize(station) == null) return true;
        return cost.linked() && !cost.linkedBlockDeleted() && stationMatches(cost.linkedStation(), station);
    }

    /** The station filter as the website reads it: trimmed, and null when blank. */
    public static String normalize(String station) {
        return station == null || station.isBlank() ? null : station.trim();
    }

    private static boolean inRange(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && (from == null || !date.isBefore(from)) && (to == null || !date.isAfter(to));
    }

    private static boolean stationMatches(String stored, String filter) {
        String wanted = normalize(filter);
        return wanted == null
                || stored != null && stored.toLowerCase(Locale.ROOT).equals(wanted.toLowerCase(Locale.ROOT));
    }
}
