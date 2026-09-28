package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.dto.BlockEvaluationRequest;
import com.angel.flexbuddy.dto.BlockEvaluationResponse;
import com.angel.flexbuddy.dto.BlockEvaluationResponse.Basis;
import com.angel.flexbuddy.dto.BlockEvaluationResponse.Verdict;
import com.angel.flexbuddy.dto.ExpenseFilter;
import com.angel.flexbuddy.dto.NetEarningsResult;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.VehicleCostMethod;

/**
 * Estimates what an offered block will net by applying the driver's own per-hour history at that station (tips,
 * miles, and linked tolls, parking, and other costs) to the offer, and compares it with a typical block there.
 */
@Service
public class BlockEvaluator {
    static final int MIN_SAMPLE = 3;
    static final int RECENT_DAYS = 90;
    private static final BigDecimal ABOUT_USUAL_PERCENT = new BigDecimal("5.0");
    private static final Set<ExpenseCategory> VEHICLE = EnumSet.of(ExpenseCategory.FUEL, ExpenseCategory.MAINTENANCE);
    private static final Set<ShiftStatus> WORKED = Set.of(ShiftStatus.COMPLETED);
    private static final int SCALE = 8;

    private final ShiftService shiftService;
    private final ExpenseService expenseService;
    private final AccountSettingsService settingsService;
    private final NetEarningsCalculator calculator;
    private final UserTimeService userTime;

    public BlockEvaluator(ShiftService shiftService, ExpenseService expenseService,
            AccountSettingsService settingsService, NetEarningsCalculator calculator, UserTimeService userTime) {
        this.shiftService = shiftService;
        this.expenseService = expenseService;
        this.settingsService = settingsService;
        this.calculator = calculator;
        this.userTime = userTime;
    }

    @Transactional(readOnly = true)
    public BlockEvaluationResponse evaluate(String email, BlockEvaluationRequest request) {
        LocalDate today = userTime.today(email);
        String station = request.station().trim();
        Sample sample = sample(email, station, today);
        AccountSettingsResponse settings = settingsService.get(email);
        Rates rates = rates(email, sample, settings.vehicleCostMethod(), today);
        int minutes = request.hours().multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValueExact();

        BigDecimal tips = request.expectedTips() != null ? request.expectedTips() : rates.tipsPerMinute().multiply(BigDecimal.valueOf(minutes));
        NetEarningsResult offer = estimate(request.offeredPay(), tips, minutes, rates, settings);
        NetEarningsResult usual = sample.shifts().isEmpty() ? null : estimate(
                rates.basePerMinute().multiply(BigDecimal.valueOf(minutes)),
                rates.tipsPerMinute().multiply(BigDecimal.valueOf(minutes)), minutes, rates, settings);
        BigDecimal difference = usual == null ? null : differencePercent(offer.netHourlyRate(), usual.netHourlyRate());
        BigDecimal baseHourly = usualBaseHourly(sample.shifts());

        return new BlockEvaluationResponse(sample.basis(), sample.shifts().size(),
                sample.basis() == Basis.ACCOUNT ? station : sample.shifts().getFirst().getStation().trim(),
                request.offeredPay().multiply(BigDecimal.valueOf(60)).divide(BigDecimal.valueOf(minutes), 2, RoundingMode.HALF_UP),
                offer.grossHourlyRate(), money(tips), rates.milesPerMinute() == null ? null : offer.miles(),
                offer.vehicleCost(), offer.outOfPocketExpenses(), offer.netEarnings(), offer.netHourlyRate(),
                usual == null ? null : usual.netHourlyRate(), usual == null ? null : usual.grossHourlyRate(),
                verdict(difference), difference, baseHourly, surge(request.offeredPay(), baseHourly, minutes));
    }

    /**
     * The base pay per hour seen most often in the sample, rounded to the dollar: Flex pays most blocks at a
     * station's standard rate, so the most common value is that rate and anything above it is surge. A tie goes to
     * the higher rate, so surge is never overstated.
     */
    static BigDecimal usualBaseHourly(List<Shift> shifts) {
        java.util.Map<BigDecimal, Long> counts = shifts.stream().filter(shift -> shift.getWorkedMinutes() > 0)
                .map(shift -> shift.getEarnedBasePay().multiply(BigDecimal.valueOf(60))
                        .divide(BigDecimal.valueOf(shift.getWorkedMinutes()), 0, RoundingMode.HALF_UP))
                .collect(java.util.stream.Collectors.groupingBy(rate -> rate, java.util.stream.Collectors.counting()));
        return counts.entrySet().stream()
                .max(java.util.Map.Entry.<BigDecimal, Long>comparingByValue().thenComparing(java.util.Map.Entry.comparingByKey()))
                .map(entry -> entry.getKey().setScale(2))
                .orElse(null);
    }

    private static BigDecimal surge(BigDecimal offeredPay, BigDecimal baseHourly, int minutes) {
        if (baseHourly == null) return null;
        BigDecimal base = baseHourly.multiply(BigDecimal.valueOf(minutes)).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
        return offeredPay.subtract(base).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);
    }

    /** The station's last 90 days, then its whole history, then every block on the account. */
    private Sample sample(String email, String station, LocalDate today) {
        LocalDate recentStart = today.minusDays(RECENT_DAYS - 1L);
        List<Shift> recent = worked(email, station, recentStart, today);
        if (recent.size() >= MIN_SAMPLE) return new Sample(Basis.STATION_90_DAYS, recent, recentStart);
        List<Shift> allTime = worked(email, station, null, today);
        if (allTime.size() >= MIN_SAMPLE) return new Sample(Basis.STATION_ALL_TIME, allTime, null);
        return new Sample(Basis.ACCOUNT, worked(email, null, null, today), null);
    }

    private List<Shift> worked(String email, String station, LocalDate from, LocalDate to) {
        return shiftService.findFiltered(email, ShiftFilter.report(from, to, station, null).withStatuses(WORKED)).stream()
                .filter(shift -> shift.getWorkedMinutes() > 0)
                .toList();
    }

    private Rates rates(String email, Sample sample, VehicleCostMethod method, LocalDate today) {
        List<Shift> shifts = sample.shifts();
        int minutes = shifts.stream().mapToInt(Shift::getWorkedMinutes).sum();
        if (minutes == 0) return Rates.NONE;
        BigDecimal base = shifts.stream().map(Shift::getEarnedBasePay).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tips = shifts.stream().map(Shift::getEarnedTips).reduce(BigDecimal.ZERO, BigDecimal::add);

        // Blocks with no miles logged say nothing about distance, so they are left out of the miles rate.
        List<Shift> withMiles = shifts.stream().filter(shift -> shift.getMiles() != null).toList();
        int milesMinutes = withMiles.stream().mapToInt(Shift::getWorkedMinutes).sum();
        BigDecimal milesPerMinute = milesMinutes == 0 ? null
                : perMinute(withMiles.stream().map(Shift::getMiles).reduce(BigDecimal.ZERO, BigDecimal::add), milesMinutes);

        EnumMap<ExpenseCategory, BigDecimal> linked = new EnumMap<>(ExpenseCategory.class);
        for (Expense expense : expenseService.findForShifts(email, shifts)) {
            if (!VEHICLE.contains(expense.getCategory())) linked.merge(expense.getCategory(), expense.getAmount(), BigDecimal::add);
        }
        EnumMap<ExpenseCategory, BigDecimal> otherPerMinute = new EnumMap<>(ExpenseCategory.class);
        linked.forEach((category, total) -> otherPerMinute.put(category, perMinute(total, minutes)));

        VehicleRate vehicle = method == VehicleCostMethod.ACTUAL_EXPENSES ? vehicleRate(email, sample.from(), today) : VehicleRate.NONE;
        return new Rates(perMinute(base, minutes), perMinute(tips, minutes), milesPerMinute, Map.copyOf(otherPerMinute),
                vehicle.perMile(), vehicle.perMinute());
    }

    /**
     * Fuel and maintenance are rarely tied to one block, so the account's spending over the same window is spread
     * across the miles driven in it, or across the hours worked when no miles were logged.
     */
    private VehicleRate vehicleRate(String email, LocalDate from, LocalDate today) {
        BigDecimal spent = expenseService.findFiltered(email, new ExpenseFilter(from, today, null, null, null, null)).stream()
                .filter(expense -> VEHICLE.contains(expense.getCategory()))
                .filter(expense -> expense.getShift() == null || expense.getShift().getStatus() != ShiftStatus.SCHEDULED)
                .map(Expense::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (spent.signum() == 0) return VehicleRate.NONE;
        List<Shift> driven = shiftService.findFiltered(email,
                ShiftFilter.report(from, today, null, null).withStatuses(ShiftStatus.HISTORY));
        BigDecimal miles = driven.stream().map(Shift::getCountedMiles).filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        int minutes = driven.stream().mapToInt(Shift::getWorkedMinutes).sum();
        return new VehicleRate(miles.signum() == 0 ? null : spent.divide(miles, SCALE, RoundingMode.HALF_UP),
                minutes == 0 ? BigDecimal.ZERO : perMinute(spent, minutes));
    }

    /** Runs the offer through the same calculator as the reports, as one block with the expected costs attached. */
    private NetEarningsResult estimate(BigDecimal pay, BigDecimal tips, int minutes, Rates rates,
            AccountSettingsResponse settings) {
        BigDecimal length = BigDecimal.valueOf(minutes);
        Shift block = new Shift(null, "", null, LocalTime.MIDNIGHT, LocalTime.MIDNIGHT.plusMinutes(minutes), pay, tips);
        BigDecimal miles = rates.milesPerMinute() == null ? null
                : rates.milesPerMinute().multiply(length).setScale(1, RoundingMode.HALF_UP);
        block.setMiles(miles);

        List<Expense> costs = new ArrayList<>();
        rates.otherPerMinute().forEach((category, rate) -> costs.add(cost(category, rate.multiply(length))));
        if (settings.vehicleCostMethod() == VehicleCostMethod.ACTUAL_EXPENSES) {
            BigDecimal vehicle = rates.vehiclePerMile() != null && miles != null
                    ? rates.vehiclePerMile().multiply(miles) : rates.vehiclePerMinute().multiply(length);
            costs.add(cost(ExpenseCategory.FUEL, vehicle));
        }
        return calculator.calculate(List.of(block), costs, settings.vehicleCostMethod(), settings.mileageRate());
    }

    private static Expense cost(ExpenseCategory category, BigDecimal amount) {
        Expense expense = new Expense();
        expense.setCategory(category);
        expense.setAmount(amount);
        return expense;
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

    private record Sample(Basis basis, List<Shift> shifts, LocalDate from) {
    }

    private record Rates(BigDecimal basePerMinute, BigDecimal tipsPerMinute, BigDecimal milesPerMinute,
            Map<ExpenseCategory, BigDecimal> otherPerMinute, BigDecimal vehiclePerMile, BigDecimal vehiclePerMinute) {
        static final Rates NONE = new Rates(BigDecimal.ZERO, BigDecimal.ZERO, null, Map.of(), null, BigDecimal.ZERO);
    }

    private record VehicleRate(BigDecimal perMile, BigDecimal perMinute) {
        static final VehicleRate NONE = new VehicleRate(null, BigDecimal.ZERO);
    }
}
