package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.dto.BlockEvaluationRequest;
import com.angel.flexbuddy.dto.BlockEvaluationResponse;
import com.angel.flexbuddy.dto.BlockEvaluationResponse.Basis;
import com.angel.flexbuddy.dto.BlockEvaluationResponse.Verdict;
import com.angel.flexbuddy.dto.ExpenseFilter;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.VehicleCostMethod;

@ExtendWith(MockitoExtension.class)
class BlockEvaluatorTest {

    private static final String EMAIL = "angel@example.com";
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

    @Mock ShiftService shiftService;
    @Mock ExpenseService expenseService;
    @Mock AccountSettingsService settingsService;
    @Mock UserTimeService userTime;
    @Spy NetEarningsCalculator calculator = new NetEarningsCalculator();
    @InjectMocks BlockEvaluator evaluator;

    private final List<Shift> shifts = new ArrayList<>();
    private final List<Expense> expenses = new ArrayList<>();
    private long nextId = 1;

    /** The fakes filter like the repositories do: station case-insensitively, dates inclusive, statuses by set. */
    @BeforeEach
    void setUp() {
        lenient().when(userTime.today(EMAIL)).thenReturn(TODAY);
        useMethod(VehicleCostMethod.STANDARD_MILEAGE);
        lenient().when(shiftService.findFiltered(eq(EMAIL), any(ShiftFilter.class))).thenAnswer(invocation -> {
            ShiftFilter filter = invocation.getArgument(1);
            return shifts.stream()
                    .filter(shift -> filter.station() == null || shift.getStation().equalsIgnoreCase(filter.station()))
                    .filter(shift -> filter.from() == null || !shift.getDate().isBefore(filter.from()))
                    .filter(shift -> filter.to() == null || !shift.getDate().isAfter(filter.to()))
                    .filter(shift -> filter.statuses().contains(shift.getStatus()))
                    .toList();
        });
        lenient().when(expenseService.findForShifts(eq(EMAIL), any())).thenAnswer(invocation -> {
            List<Shift> sample = invocation.getArgument(1);
            Set<Long> ids = sample.stream().map(Shift::getId).collect(Collectors.toSet());
            return expenses.stream().filter(expense -> expense.getShift() != null && ids.contains(expense.getShift().getId())).toList();
        });
        lenient().when(expenseService.findFiltered(eq(EMAIL), any(ExpenseFilter.class))).thenAnswer(invocation -> {
            ExpenseFilter filter = invocation.getArgument(1);
            return expenses.stream()
                    .filter(expense -> filter.from() == null || !expense.getDate().isBefore(filter.from()))
                    .filter(expense -> filter.to() == null || !expense.getDate().isAfter(filter.to()))
                    .toList();
        });
    }

    @Test
    void twelveStationBlocksGiveTheCalculatorsNetPerHourForTheOffer() {
        for (int day = 1; day <= 12; day++) block("VEA7", TODAY.minusDays(day), 240, "84.00", "6.00", "22.0");
        expense(shifts.getFirst(), ExpenseCategory.TOLL, "4.00", TODAY.minusDays(1));

        BlockEvaluationResponse result = evaluate("VEA7", "4", "90.00", null);

        assertThat(result.basis()).isEqualTo(Basis.STATION_90_DAYS);
        assertThat(result.sampleSize()).isEqualTo(12);
        assertThat(result.station()).isEqualTo("VEA7");
        assertThat(result.offeredHourly()).isEqualByComparingTo("22.50");
        assertThat(result.estimatedTips()).isEqualByComparingTo("6.00");
        assertThat(result.estimatedMiles()).isEqualByComparingTo("22.0");
        // 22 miles at $0.70 and one $4 toll spread over 48 hours.
        assertThat(result.estimatedVehicleCost()).isEqualByComparingTo("15.40");
        assertThat(result.estimatedOtherExpenses()).isEqualByComparingTo("0.33");
        assertThat(result.estimatedNet()).isEqualByComparingTo("80.27");
        assertThat(result.estimatedNetHourly()).isEqualByComparingTo("20.07");
        assertThat(result.grossHourly()).isEqualByComparingTo("24.00");
        assertThat(result.usualGrossHourly()).isEqualByComparingTo("22.50");
        assertThat(result.usualNetHourly()).isEqualByComparingTo("18.57");
        assertThat(result.differencePercent()).isEqualByComparingTo("8.1");
        assertThat(result.verdict()).isEqualTo(Verdict.ABOVE_USUAL);
    }

    @Test
    void stationMatchIgnoresCaseAndSpacesAndNamesTheStationAsLogged() {
        for (int day = 1; day <= 3; day++) block("VEA7", TODAY.minusDays(day), 240, "80.00", "0", null);

        BlockEvaluationResponse result = evaluate("  vea7 ", "4", "80.00", null);

        assertThat(result.basis()).isEqualTo(Basis.STATION_90_DAYS);
        assertThat(result.station()).isEqualTo("VEA7");
    }

    @Test
    void fallsBackToTheStationsWholeHistoryThenToTheAccount() {
        block("VEA7", TODAY.minusDays(3), 240, "80.00", "0", null);
        block("VEA7", TODAY.minusDays(4), 240, "80.00", "0", null);
        block("VEA7", TODAY.minusDays(200), 240, "60.00", "0", null);
        block("DAX5", TODAY.minusDays(5), 120, "50.00", "0", null);

        BlockEvaluationResponse station = evaluate("VEA7", "4", "80.00", null);
        assertThat(station.basis()).isEqualTo(Basis.STATION_ALL_TIME);
        assertThat(station.sampleSize()).isEqualTo(3);
        assertThat(station.usualGrossHourly()).isEqualByComparingTo("18.33");

        BlockEvaluationResponse account = evaluate("DAX5", "2", "50.00", null);
        assertThat(account.basis()).isEqualTo(Basis.ACCOUNT);
        assertThat(account.sampleSize()).isEqualTo(4);
        assertThat(account.station()).isEqualTo("DAX5");
    }

    @Test
    void onlyWorkedBlocksCountTowardTheSample() {
        block("VEA7", TODAY.minusDays(1), 240, "80.00", "0", null);
        block("VEA7", TODAY.minusDays(2), 240, "80.00", "0", null);
        withStatus(block("VEA7", TODAY.minusDays(3), 240, "20.00", "0", null), ShiftStatus.CANCELLED);
        withStatus(block("VEA7", TODAY.minusDays(4), 240, "0", "0", null), ShiftStatus.FORFEITED);
        withStatus(block("VEA7", TODAY.plusDays(1), 240, "84.00", "0", null), ShiftStatus.SCHEDULED);

        BlockEvaluationResponse result = evaluate("VEA7", "4", "80.00", null);

        assertThat(result.basis()).isEqualTo(Basis.ACCOUNT);
        assertThat(result.sampleSize()).isEqualTo(2);
    }

    @Test
    void expectedTipsReplaceTheStationTipAverage() {
        for (int day = 1; day <= 3; day++) block("VEA7", TODAY.minusDays(day), 240, "84.00", "12.00", null);

        assertThat(evaluate("VEA7", "4", "84.00", null).estimatedTips()).isEqualByComparingTo("12.00");
        BlockEvaluationResponse noTips = evaluate("VEA7", "4", "84.00", "0");
        assertThat(noTips.estimatedTips()).isEqualByComparingTo("0.00");
        assertThat(noTips.estimatedNet()).isEqualByComparingTo("84.00");
        assertThat(noTips.usualNetHourly()).isEqualByComparingTo("24.00");
    }

    @Test
    void actualExpensesSpreadFuelOverMilesInsteadOfUsingTheMileageRate() {
        useMethod(VehicleCostMethod.ACTUAL_EXPENSES);
        for (int day = 1; day <= 3; day++) block("VEA7", TODAY.minusDays(day), 240, "84.00", "0", "20.0");
        block("DAX5", TODAY.minusDays(4), 240, "84.00", "0", "40.0");
        expense(null, ExpenseCategory.FUEL, "25.00", TODAY.minusDays(2));
        expense(null, ExpenseCategory.MAINTENANCE, "5.00", TODAY.minusDays(3));
        expense(null, ExpenseCategory.FUEL, "500.00", TODAY.minusDays(400));

        BlockEvaluationResponse result = evaluate("VEA7", "4", "84.00", null);

        // $30 over the 100 miles driven in the last 90 days, times this station's 20 miles.
        assertThat(result.estimatedMiles()).isEqualByComparingTo("20.0");
        assertThat(result.estimatedVehicleCost()).isEqualByComparingTo("6.00");
        assertThat(result.estimatedNet()).isEqualByComparingTo("78.00");
    }

    @Test
    void blocksWithoutMilesLeaveTheMilesEstimateEmpty() {
        for (int day = 1; day <= 3; day++) block("VEA7", TODAY.minusDays(day), 240, "84.00", "0", null);

        BlockEvaluationResponse result = evaluate("VEA7", "4", "84.00", null);

        assertThat(result.estimatedMiles()).isNull();
        assertThat(result.estimatedVehicleCost()).isEqualByComparingTo("0.00");
    }

    @Test
    void verdictTreatsExactlyFivePercentEitherWayAsAboutUsual() {
        for (int day = 1; day <= 3; day++) block("VEA7", TODAY.minusDays(day), 240, "80.00", "0", null);

        assertVerdict("84.00", "5.0", Verdict.ABOUT_USUAL);
        assertVerdict("76.00", "-5.0", Verdict.ABOUT_USUAL);
        assertVerdict("84.40", "5.5", Verdict.ABOVE_USUAL);
        assertVerdict("75.60", "-5.5", Verdict.BELOW_USUAL);
    }

    @Test
    void aNewAccountGetsTheOffersGrossOnly() {
        BlockEvaluationResponse result = evaluate("VEA7", "3.5", "70.00", null);

        assertThat(result.basis()).isEqualTo(Basis.ACCOUNT);
        assertThat(result.sampleSize()).isZero();
        assertThat(result.estimatedTips()).isEqualByComparingTo("0.00");
        assertThat(result.estimatedMiles()).isNull();
        assertThat(result.estimatedNet()).isEqualByComparingTo("70.00");
        assertThat(result.estimatedNetHourly()).isEqualByComparingTo("20.00");
        assertThat(result.usualNetHourly()).isNull();
        assertThat(result.verdict()).isNull();
        assertThat(result.differencePercent()).isNull();
    }

    private void assertVerdict(String pay, String difference, Verdict verdict) {
        BlockEvaluationResponse result = evaluate("VEA7", "4", pay, null);
        assertThat(result.differencePercent()).isEqualByComparingTo(difference);
        assertThat(result.verdict()).isEqualTo(verdict);
    }

    private BlockEvaluationResponse evaluate(String station, String hours, String pay, String tips) {
        return evaluator.evaluate(EMAIL, new BlockEvaluationRequest(station, new BigDecimal(hours), new BigDecimal(pay),
                tips == null ? null : new BigDecimal(tips)));
    }

    private void useMethod(VehicleCostMethod method) {
        lenient().when(settingsService.get(EMAIL)).thenReturn(
                new AccountSettingsResponse(method, new BigDecimal("0.70"), new BigDecimal("0.70"), 2026));
    }

    private Shift block(String station, LocalDate date, int minutes, String base, String tips, String miles) {
        LocalTime start = LocalTime.of(8, 0);
        Shift shift = new Shift(nextId++, station, date, start, start.plusMinutes(minutes), new BigDecimal(base),
                new BigDecimal(tips));
        if (miles != null) shift.setMiles(new BigDecimal(miles));
        shifts.add(shift);
        return shift;
    }

    private static void withStatus(Shift shift, ShiftStatus status) {
        shift.setStatus(status);
    }

    private void expense(Shift shift, ExpenseCategory category, String amount, LocalDate date) {
        Expense expense = new Expense();
        expense.setShift(shift);
        expense.setCategory(category);
        expense.setAmount(new BigDecimal(amount));
        expense.setDate(date);
        expenses.add(expense);
    }

    @Test
    void theOfferAboveTheStationsUsualBaseRateIsSurge() {
        // Two blocks at the standard $18 an hour and one surged block at $24 an hour.
        block("VEA7", TODAY.minusDays(1), 240, "72.00", "0", null);
        block("VEA7", TODAY.minusDays(2), 240, "72.00", "0", null);
        block("VEA7", TODAY.minusDays(3), 240, "96.00", "0", null);

        BlockEvaluationResponse surged = evaluate("VEA7", "4", "84.00", null);
        BlockEvaluationResponse plain = evaluate("VEA7", "4", "70.00", null);

        assertThat(surged.usualBaseHourly()).isEqualByComparingTo("18.00");
        assertThat(surged.surgePay()).isEqualByComparingTo("12.00");
        assertThat(plain.surgePay()).isEqualByComparingTo("0.00");
    }

    @Test
    void aTieBetweenRatesTakesTheHigherSoSurgeIsNotOverstated() {
        block("VEA7", TODAY.minusDays(1), 240, "72.00", "0", null);
        block("VEA7", TODAY.minusDays(2), 240, "80.00", "0", null);
        block("VEA7", TODAY.minusDays(3), 180, "81.00", "0", null);

        assertThat(evaluate("VEA7", "4", "84.00", null).usualBaseHourly()).isEqualByComparingTo("27.00");
        assertThat(BlockEvaluator.usualBaseHourly(List.of())).isNull();
    }
}
