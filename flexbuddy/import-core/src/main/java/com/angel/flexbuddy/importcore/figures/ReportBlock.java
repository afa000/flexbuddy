package com.angel.flexbuddy.importcore.figures;

import java.math.BigDecimal;

/**
 * A block for the earnings report: the {@link Block} plus its id, actual minutes (see
 * {@link RouteRules#actualMinutes}) and route counts. The consumer passes only the signed-in account's live blocks.
 */
public record ReportBlock(Long id, Block block, Integer actualMinutes, Integer stops, Integer packages,
        Integer returns) {

    public int clockedMinutes() {
        return RouteRules.clockedMinutes(block.status(), block.start(), block.end(), actualMinutes);
    }

    public Integer finishedEarlyMinutes() {
        return RouteRules.finishedEarlyMinutes(block.status(), block.start(), block.end(), actualMinutes);
    }

    public BigDecimal minutesPerStop() {
        return RouteRules.minutesPerStop(block.status(), block.start(), block.end(), actualMinutes, stops);
    }

    public Integer returnsBase() {
        return RouteRules.returnsBase(stops, packages, returns);
    }

    public BigDecimal returnsRate() {
        return RouteRules.returnsRate(stops, packages, returns);
    }
}
