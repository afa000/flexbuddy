package com.angel.flexbuddy.importcore.figures;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * "Is this offer worth it?": estimates what an offered block will net by applying the driver's own per-hour history
 * (tips, miles, and linked tolls, parking and other costs) to the offer, and compares it with a typical block.
 *
 * <p>The consumer selects the signed-in account's live data: {@link #chooseSample} picks the history from its worked
 * blocks, and {@link #evaluate} takes the expenses linked to that sample and, for actual expenses, the countable
 * expenses and finished blocks from the sample's start date to today.
 */
public final class BlockOffers {
    /** A station needs this many worked blocks in a window before its own history is used. */
    public static final int MIN_SAMPLE = 3;
    public static final int RECENT_DAYS = 90;
    private static final BigDecimal ABOUT_USUAL_PERCENT = new BigDecimal("5.0");
    private static final Set<CostCategory> VEHICLE = EnumSet.of(CostCategory.FUEL, CostCategory.MAINTENANCE);
    private static final int SCALE = 8;

    public enum Basis { STATION_90_DAYS, STATION_ALL_TIME, ACCOUNT }

    public enum Verdict { BELOW_USUAL, ABOUT_USUAL, ABOVE_USUAL }

    /** The offer as the driver typed it. Expected tips are optional; without them the history's tips rate is used. */
    public record Offer(String station, BigDecimal hours, BigDecimal offeredPay, BigDecimal expectedTips) {
    }

    /** The history used, newest first, and the first date of its window (null for all time). */
    public record Sample<T>(Basis basis, List<T> blocks, LocalDate from) {
    }

    public record Evaluation(Basis basis, int sampleSize, String station, BigDecimal offeredHourly,
            BigDecimal grossHourly, BigDecimal estimatedTips, BigDecimal estimatedMiles,
            BigDecimal estimatedVehicleCost, BigDecimal estimatedOtherExpenses, BigDecimal estimatedNet,
            BigDecimal estimatedNetHourly, BigDecimal usualNetHourly, BigDecimal usualGrossHourly, Verdict verdict,
            BigDecimal differencePercent, BigDecimal usualBaseHourly, BigDecimal surgePay) {
    }

    private record Rates(BigDecimal basePerMinute, BigDecimal tipsPerMinute, BigDecimal milesPerMinute,
            Map<CostCategory, BigDecimal> otherPerMinute, BigDecimal vehiclePerMile, BigDecimal vehiclePerMinute) {
        static final Rates NONE = new Rates(BigDecimal.ZERO, BigDecimal.ZERO, null, Map.of(), null, BigDecimal.ZERO);
    }

    private record VehicleRate(BigDecimal perMile, BigDecimal perMinute) {
        static final VehicleRate NONE = new VehicleRate(null, BigDecimal.ZERO);
    }

    private BlockOffers() {
    }

    /**
     * The station's last 90 days, then its whole history, then every block on the account, each counting only
     * completed blocks with worked minutes dated up to today. {@code blocks} are the account's live blocks, newest
     * first; the order is kept. The station is compared ignoring case and the offer's surrounding spaces.
     *
     * @throws IllegalArgumentException if the station is blank: the consumer validates it first, as the website does
     */
    public static <T> Sample<T> chooseSample(List<T> blocks, Function<? super T, Block> view, String station,
            LocalDate today) {
        if (station == null || station.isBlank()) throw new IllegalArgumentException("The offer needs a station");
        String wanted = station.trim();
        LocalDate recentStart = today.minusDays(RECENT_DAYS - 1L);
        List<T> recent = worked(blocks, view, wanted, recentStart, today);
        if (recent.size() >= MIN_SAMPLE) return new Sample<>(Basis.STATION_90_DAYS, recent, recentStart);
        List<T> allTime = worked(blocks, view, wanted, null, today);
        if (allTime.size() >= MIN_SAMPLE) return new Sample<>(Basis.STATION_ALL_TIME, allTime, null);
        return new Sample<>(Basis.ACCOUNT, worked(blocks, view, null, null, today), null);
    }

    private static <T> List<T> worked(List<T> blocks, Function<? super T, Block> view, String station,
            LocalDate from, LocalDate to) {
        List<T> found = new ArrayList<>();
        for (T item : blocks) {
            Block block = view.apply(item);
            if (block.effectiveStatus() != BlockStatus.COMPLETED || block.workedMinutes() <= 0) continue;
            if (!within(block.date(), from, to)) continue;
            if (station != null && (block.station() == null || !block.station().equalsIgnoreCase(station))) continue;
            found.add(item);
        }
        return Collections.unmodifiableList(found);
    }

    /**
     * Evaluates the offer against the chosen sample.
     *
     * @param linkedCosts  the countable expenses linked to the sample's blocks; fuel and maintenance are ignored
     * @param windowCosts  for actual expenses, the countable expenses from the sample's start to today
     * @param windowBlocks for actual expenses, the finished blocks from the sample's start to today
     */
    public static Evaluation evaluate(Offer offer, Sample<Block> sample, List<Cost> linkedCosts,
            List<Cost> windowCosts, List<Block> windowBlocks, VehicleCostMethod method, MileageRates mileageRates,
            LocalDate today) {
        String station = offer.station().trim();
        List<Block> blocks = sample.blocks();
        Rates rates = rates(blocks, linkedCosts, method == VehicleCostMethod.ACTUAL_EXPENSES
                ? vehicleRate(windowCosts, windowBlocks, sample.from(), today) : VehicleRate.NONE);
        int minutes = offer.hours().multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValueExact();
        BigDecimal length = BigDecimal.valueOf(minutes);

        BigDecimal tips = offer.expectedTips() != null ? offer.expectedTips() : rates.tipsPerMinute().multiply(length);
        NetEarnings estimate = estimate(offer.offeredPay(), tips, minutes, rates, method, mileageRates, today);
        NetEarnings usual = blocks.isEmpty() ? null : estimate(rates.basePerMinute().multiply(length),
                rates.tipsPerMinute().multiply(length), minutes, rates, method, mileageRates, today);
        BigDecimal difference = usual == null ? null
                : differencePercent(estimate.netHourlyRate(), usual.netHourlyRate());
        BigDecimal baseHourly = usualBaseHourly(blocks);

        return new Evaluation(sample.basis(), blocks.size(),
                sample.basis() == Basis.ACCOUNT ? station : blocks.get(0).station().trim(),
                offer.offeredPay().multiply(BigDecimal.valueOf(60)).divide(length, 2, RoundingMode.HALF_UP),
                estimate.grossHourlyRate(), money(tips), rates.milesPerMinute() == null ? null : estimate.miles(),
                estimate.vehicleCost(), estimate.outOfPocketExpenses(), estimate.netEarnings(),
                estimate.netHourlyRate(), usual == null ? null : usual.netHourlyRate(),
                usual == null ? null : usual.grossHourlyRate(), verdict(difference), difference, baseHourly,
                surge(offer.offeredPay(), baseHourly, minutes));
    }

    /**
     * The base pay per hour seen most often in the sample, rounded to the dollar: Flex pays most blocks at a
     * station's standard rate, so the most common value is that rate and anything above it is surge. A tie goes to
     * the higher rate, so surge is never overstated. Null without a block with worked minutes.
     */
    public static BigDecimal usualBaseHourly(List<Block> blocks) {
        Map<BigDecimal, Long> counts = new HashMap<>();
        for (Block block : blocks) {
            int minutes = block.workedMinutes();
            if (minutes <= 0) continue;
            BigDecimal rate = block.earnedBasePay().multiply(BigDecimal.valueOf(60))
                    .divide(BigDecimal.valueOf(minutes), 0, RoundingMode.HALF_UP);
            counts.merge(rate, 1L, Long::sum);
        }
        BigDecimal best = null;
        long bestCount = 0;
        for (Map.Entry<BigDecimal, Long> entry : counts.entrySet()) {
            long count = entry.getValue();
            if (best == null || count > bestCount || (count == bestCount && entry.getKey().compareTo(best) > 0)) {
                best = entry.getKey();
                bestCount = count;
            }
        }
        return best == null ? null : best.setScale(2);
    }

    private static BigDecimal surge(BigDecimal offeredPay, BigDecimal baseHourly, int minutes) {
        if (baseHourly == null) return null;
        BigDecimal base = baseHourly.multiply(BigDecimal.valueOf(minutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        return offeredPay.subtract(base).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    private static Rates rates(List<Block> blocks, List<Cost> linkedCosts, VehicleRate vehicle) {
        int minutes = 0;
        BigDecimal base = BigDecimal.ZERO;
        BigDecimal tips = BigDecimal.ZERO;
        // Blocks with no miles logged say nothing about distance, so they are left out of the miles rate.
        int milesMinutes = 0;
        BigDecimal miles = BigDecimal.ZERO;
        for (Block block : blocks) {
            minutes += block.workedMinutes();
            base = base.add(block.earnedBasePay());
            tips = tips.add(block.earnedTips());
            if (block.miles() != null) {
                milesMinutes += block.workedMinutes();
                miles = miles.add(block.miles());
            }
        }
        if (minutes == 0) return Rates.NONE;
        BigDecimal milesPerMinute = milesMinutes == 0 ? null : perMinute(miles, milesMinutes);

        EnumMap<CostCategory, BigDecimal> linked = new EnumMap<>(CostCategory.class);
        for (Cost cost : linkedCosts) {
            if (!VEHICLE.contains(cost.category())) linked.merge(cost.category(), cost.amount(), BigDecimal::add);
        }
        EnumMap<CostCategory, BigDecimal> otherPerMinute = new EnumMap<>(CostCategory.class);
        for (Map.Entry<CostCategory, BigDecimal> entry : linked.entrySet()) {
            otherPerMinute.put(entry.getKey(), perMinute(entry.getValue(), minutes));
        }
        return new Rates(perMinute(base, minutes), perMinute(tips, minutes), milesPerMinute,
                Collections.unmodifiableMap(otherPerMinute), vehicle.perMile(), vehicle.perMinute());
    }

    /**
     * Fuel and maintenance are rarely tied to one block, so the account's spending over the same window is spread
     * across the miles driven in it, or across the hours worked when no miles were logged.
     */
    private static VehicleRate vehicleRate(List<Cost> costs, List<Block> blocks, LocalDate from, LocalDate today) {
        BigDecimal spent = BigDecimal.ZERO;
        for (Cost cost : costs) {
            if (VEHICLE.contains(cost.category()) && within(cost.date(), from, today)) spent = spent.add(cost.amount());
        }
        if (spent.signum() == 0) return VehicleRate.NONE;
        BigDecimal miles = BigDecimal.ZERO;
        int minutes = 0;
        for (Block block : blocks) {
            if (!within(block.date(), from, today)) continue;
            BigDecimal counted = block.countedMiles();
            if (counted != null) miles = miles.add(counted);
            minutes += block.workedMinutes();
        }
        return new VehicleRate(miles.signum() == 0 ? null : spent.divide(miles, SCALE, RoundingMode.HALF_UP),
                minutes == 0 ? BigDecimal.ZERO : perMinute(spent, minutes));
    }

    /** Runs the offer through the same calculator as the reports, as one block with the expected costs attached. */
    private static NetEarnings estimate(BigDecimal pay, BigDecimal tips, int minutes, Rates rates,
            VehicleCostMethod method, MileageRates mileageRates, LocalDate today) {
        BigDecimal length = BigDecimal.valueOf(minutes);
        BigDecimal miles = rates.milesPerMinute() == null ? null
                : rates.milesPerMinute().multiply(length).setScale(1, RoundingMode.HALF_UP);
        Block block = new Block(BlockStatus.COMPLETED, "", today, LocalTime.MIDNIGHT,
                LocalTime.MIDNIGHT.plusMinutes(minutes), pay, tips, miles);

        List<Cost> costs = new ArrayList<>();
        for (Map.Entry<CostCategory, BigDecimal> entry : rates.otherPerMinute().entrySet()) {
            costs.add(new Cost(entry.getKey(), entry.getValue().multiply(length), null));
        }
        if (method == VehicleCostMethod.ACTUAL_EXPENSES) {
            BigDecimal vehicle = rates.vehiclePerMile() != null && miles != null
                    ? rates.vehiclePerMile().multiply(miles) : rates.vehiclePerMinute().multiply(length);
            costs.add(new Cost(CostCategory.FUEL, vehicle, null));
        }
        return NetEarnings.calculate(List.of(block), costs, method, mileageRates);
    }

    private static boolean within(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && (from == null || !date.isBefore(from)) && !date.isAfter(to);
    }

    private static BigDecimal differencePercent(BigDecimal offered, BigDecimal usual) {
        if (usual.signum() == 0) return null;
        return offered.subtract(usual).multiply(BigDecimal.valueOf(100)).divide(usual.abs(), 1, RoundingMode.HALF_UP);
    }

    /** Uses the rounded difference, so a block shown as "+5.0%" is never called above usual. */
    private static Verdict verdict(BigDecimal difference) {
        if (difference == null) return null;
        if (difference.compareTo(ABOUT_USUAL_PERCENT) > 0) return Verdict.ABOVE_USUAL;
        if (difference.compareTo(ABOUT_USUAL_PERCENT.negate()) < 0) return Verdict.BELOW_USUAL;
        return Verdict.ABOUT_USUAL;
    }

    private static BigDecimal perMinute(BigDecimal total, int minutes) {
        return total.divide(BigDecimal.valueOf(minutes), SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
