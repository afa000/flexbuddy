package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.dto.BlockEvaluationRequest;
import com.angel.flexbuddy.dto.BlockEvaluationResponse;
import com.angel.flexbuddy.dto.BlockEvaluationResponse.Basis;
import com.angel.flexbuddy.dto.BlockEvaluationResponse.Verdict;
import com.angel.flexbuddy.dto.ExpenseFilter;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.importcore.figures.Block;
import com.angel.flexbuddy.importcore.figures.BlockOffers;
import com.angel.flexbuddy.importcore.figures.Cost;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.VehicleCostMethod;

/**
 * Estimates what an offered block will net by applying the driver's own per-hour history at that station (tips,
 * miles, and linked tolls, parking, and other costs) to the offer, and compares it with a typical block there. The
 * signed-in account's data is read here; the arithmetic is the shared library's {@link BlockOffers}.
 */
@Service
public class BlockEvaluator {
    private static final Set<ShiftStatus> WORKED = Set.of(ShiftStatus.COMPLETED);

    private final ShiftService shiftService;
    private final ExpenseService expenseService;
    private final AccountSettingsService settingsService;
    private final UserTimeService userTime;

    public BlockEvaluator(ShiftService shiftService, ExpenseService expenseService,
            AccountSettingsService settingsService, UserTimeService userTime) {
        this.shiftService = shiftService;
        this.expenseService = expenseService;
        this.settingsService = settingsService;
        this.userTime = userTime;
    }

    @Transactional(readOnly = true)
    public BlockEvaluationResponse evaluate(String email, BlockEvaluationRequest request) {
        LocalDate today = userTime.today(email);
        // The account's worked blocks up to today, newest first; the library picks the station's history from them.
        List<Shift> worked = shiftService.findFiltered(email,
                ShiftFilter.report(null, today, null, null).withStatuses(WORKED));
        BlockOffers.Sample<Shift> sample = BlockOffers.chooseSample(worked, FigureInputs::block, request.station(), today);
        AccountSettingsResponse settings = settingsService.get(email);
        List<Block> blocks = FigureInputs.blocks(sample.blocks());

        List<Cost> linked = blocks.isEmpty() ? List.of()
                : FigureInputs.costs(expenseService.findForShifts(email, sample.blocks()));
        List<Cost> windowCosts = List.of();
        List<Block> windowBlocks = List.of();
        if (!blocks.isEmpty() && settings.vehicleCostMethod() == VehicleCostMethod.ACTUAL_EXPENSES) {
            windowCosts = FigureInputs.costs(expenseService.countable(email, expenseService.findFiltered(email,
                    new ExpenseFilter(sample.from(), today, null, null, null, null))));
            windowBlocks = FigureInputs.blocks(shiftService.findFiltered(email,
                    ShiftFilter.report(sample.from(), today, null, null).withStatuses(ShiftStatus.HISTORY)));
        }

        BlockOffers.Evaluation result = BlockOffers.evaluate(
                new BlockOffers.Offer(request.station(), request.hours(), request.offeredPay(), request.expectedTips()),
                new BlockOffers.Sample<>(sample.basis(), blocks, sample.from()), linked, windowCosts, windowBlocks,
                FigureInputs.method(settings.vehicleCostMethod()), settings.mileageRates().figures(), today);

        return new BlockEvaluationResponse(Basis.valueOf(result.basis().name()), result.sampleSize(),
                result.station(), result.offeredHourly(), result.grossHourly(), result.estimatedTips(),
                result.estimatedMiles(), result.estimatedVehicleCost(), result.estimatedOtherExpenses(),
                result.estimatedNet(), result.estimatedNetHourly(), result.usualNetHourly(), result.usualGrossHourly(),
                result.verdict() == null ? null : Verdict.valueOf(result.verdict().name()),
                result.differencePercent(), result.usualBaseHourly(), result.surgePay());
    }

    /**
     * The base pay per hour seen most often in the sample, rounded to the dollar: Flex pays most blocks at a
     * station's standard rate, so the most common value is that rate and anything above it is surge. A tie goes to
     * the higher rate, so surge is never overstated.
     */
    static BigDecimal usualBaseHourly(List<Shift> shifts) {
        return BlockOffers.usualBaseHourly(FigureInputs.blocks(shifts));
    }
}
