package com.angel.flexbuddy.importcore.figures;

/**
 * An expense for the earnings report: the {@link Cost} plus the block it is linked to, deleted or not. The link's id,
 * status, deleted flag and station are all null for an unlinked expense, including one whose block can't be found.
 * The consumer passes only the signed-in account's live expenses.
 */
public record ReportCost(Cost cost, Long linkedBlockId, BlockStatus linkedStatus, Boolean linkedDeleted,
        String linkedStation) {

    public static ReportCost unlinked(Cost cost) {
        return new ReportCost(cost, null, null, null, null);
    }

    public boolean linked() {
        return linkedBlockId != null;
    }

    public boolean linkedBlockDeleted() {
        return Boolean.TRUE.equals(linkedDeleted);
    }

    /** Whether it waits for its block: it does while the block is live and still scheduled (see EarningsRules). */
    public boolean waits() {
        return linked() && EarningsRules.expenseWaits(linkedStatus, linkedBlockDeleted());
    }

    /** The station it counts under: its block's, or null when it is unlinked or its block is deleted. */
    public String countedStation() {
        return !linked() || linkedBlockDeleted() ? null : linkedStation;
    }
}
