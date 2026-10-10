package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.EarningsReportResponse;
import com.angel.flexbuddy.dto.EarningsBucket;
import com.angel.flexbuddy.dto.GroupBy;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.ShiftStatisticsResponse;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.repository.AppUserRepository;

@ExtendWith(MockitoExtension.class)
class ShiftReportServiceTest {

    private static final String EMAIL = "angel@example.com";
    private static final ShiftFilter ALL = ShiftFilter.report(null, null, null, null);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 12, 12, 0);

    @Mock ShiftService shiftService;
    @Mock ExpenseService expenseService;
    @Mock AccountSettingsService settingsService;
    @Mock UserTimeService userTime;
    @Spy NetEarningsCalculator calculator = new NetEarningsCalculator();
    @Spy GoalProgressCalculator goalCalculator = new GoalProgressCalculator();
    @Spy PayPeriodCalculator payPeriodCalculator = new PayPeriodCalculator();
    @Mock com.angel.flexbuddy.repository.PayoutDepositRepository deposits;
    @InjectMocks ShiftReportService reportService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(userTime.now(EMAIL)).thenReturn(NOW);
        org.mockito.Mockito.lenient().when(expenseService.findFiltered(org.mockito.ArgumentMatchers.eq(EMAIL), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());
        org.mockito.Mockito.lenient().when(settingsService.get(EMAIL)).thenReturn(
                new com.angel.flexbuddy.dto.AccountSettingsResponse(
                        com.angel.flexbuddy.model.VehicleCostMethod.STANDARD_MILEAGE,
                        new BigDecimal("0.70"), new BigDecimal("0.70"), 2025));
    }

    @Test
    void statistics_handlesNullTipsAndCalculatesHourlyValues() {
        Shift first = shift("VEA7", LocalDate.of(2026, 9, 6), "120.00", null, 240);
        Shift second = shift("VEA6", LocalDate.of(2026, 9, 7), "60.00", "20.00", 120);
        when(shiftService.findFiltered(EMAIL, ALL)).thenReturn(List.of(first, second));

        ShiftStatisticsResponse result = reportService.statistics(EMAIL, ALL);

        assertThat(result.getTotalShifts()).isEqualTo(2);
        assertThat(result.getTotalEarnings()).isEqualByComparingTo("200.00");
        assertThat(result.getAverageHourlyEarnings()).isEqualByComparingTo("33.33");
        assertThat(result.getAverageHourlyBasePay()).isEqualByComparingTo("30.00");
        assertThat(result.getAverageHourlyTips()).isEqualByComparingTo("3.33");
        assertThat(result.getTipsShareOfEarnings()).isEqualByComparingTo("10.0");
        assertThat(result.getAverageShiftMinutes()).isEqualTo(180);
    }

    @Test
    void statistics_returnsZeroedValuesForNoShifts() {
        when(shiftService.findFiltered(EMAIL, ALL)).thenReturn(List.of());

        ShiftStatisticsResponse result = reportService.statistics(EMAIL, ALL);

        assertThat(result.getTotalShifts()).isZero();
        assertThat(result.getAverageHourlyEarnings()).isEqualByComparingTo("0.00");
        assertThat(result.getTipsShareOfEarnings()).isEqualByComparingTo("0.0");
    }

    @Test
    void statistics_calculatesHoursForTodayAndPreviousSixDaysIndependentlyOfDashboardFilters() {
        LocalDate today = NOW.toLocalDate();
        ShiftFilter dashboardFilter = ShiftFilter.report(null, null, "VEA7", null);
        ShiftFilter rollingWindow = ShiftFilter.report(today.minusDays(6), today, null, null);
        when(shiftService.findFiltered(EMAIL, dashboardFilter)).thenReturn(List.of());
        when(shiftService.findFiltered(EMAIL, rollingWindow)).thenReturn(List.of(
                shift("VEA7", today.minusDays(6), "100", "0", 180),
                shift("BDL4", today, "100", "0", 270)
        ));

        ShiftStatisticsResponse result = reportService.statistics(EMAIL, dashboardFilter);

        assertThat(result.getRollingSevenDayMinutes()).isEqualTo(450);
    }

    @Test
    void stationReport_groupsCaseInsensitivelyAndOrdersByEarnings() {
        when(shiftService.findFiltered(EMAIL, ALL)).thenReturn(List.of(
                shift("VEA7", LocalDate.of(2026, 9, 1), "50", "0", 120),
                shift("vea7", LocalDate.of(2026, 9, 2), "60", "10", 120),
                shift("BDL4", LocalDate.of(2026, 9, 3), "200", "0", 240)
        ));

        EarningsReportResponse result = reportService.earnings(EMAIL, ALL, GroupBy.STATION);

        assertThat(result.buckets()).extracting(bucket -> bucket.label()).containsExactly("BDL4", "VEA7");
        assertThat(result.buckets().get(1).shifts()).isEqualTo(2);
        assertThat(result.buckets().get(1).totalEarnings()).isEqualByComparingTo("120.00");
    }

    @Test
    void weekReport_usesIsoWeekYearAndChronologicalOrdering() {
        when(shiftService.findFiltered(EMAIL, ALL)).thenReturn(List.of(
                shift("VEA7", LocalDate.of(2027, 1, 4), "100", "0", 60),
                shift("VEA7", LocalDate.of(2027, 1, 1), "100", "0", 60),
                shift("VEA7", LocalDate.of(2026, 12, 27), "100", "0", 60)
        ));

        EarningsReportResponse result = reportService.earnings(EMAIL, ALL, GroupBy.WEEK);

        assertThat(result.buckets()).extracting(bucket -> bucket.key())
                .containsExactly("2026-W52", "2026-W53", "2027-W01");
        assertThat(result.buckets().get(1).periodStart()).isEqualTo(LocalDate.of(2026, 12, 28));
        assertThat(result.buckets().get(1).periodEnd()).isEqualTo(LocalDate.of(2027, 1, 3));
    }

    @Test
    void monthAndYearReportsProduceExpectedPeriods() {
        when(shiftService.findFiltered(EMAIL, ALL)).thenReturn(List.of(
                shift("VEA7", LocalDate.of(2025, 12, 10), "50", "0", 60),
                shift("VEA7", LocalDate.of(2026, 2, 10), "75", "0", 60)
        ));

        EarningsReportResponse months = reportService.earnings(EMAIL, ALL, GroupBy.MONTH);
        EarningsReportResponse years = reportService.earnings(EMAIL, ALL, GroupBy.YEAR);

        assertThat(months.buckets()).extracting(bucket -> bucket.key()).containsExactly("2025-12", "2026-02");
        assertThat(months.buckets().get(1).periodEnd()).isEqualTo(LocalDate.of(2026, 2, 28));
        assertThat(years.buckets()).extracting(bucket -> bucket.key()).containsExactly("2025", "2026");
    }

    @Test
    void dateReportsPlaceLegacyRowsWithMissingDatesInAnUnknownBucket() {
        when(shiftService.findFiltered(EMAIL, ALL)).thenReturn(List.of(
                shift("VEA7", LocalDate.of(2026, 2, 10), "75", "0", 60),
                shift("VEA7", null, "50", "0", 60)
        ));

        for (GroupBy groupBy : List.of(GroupBy.WEEK, GroupBy.MONTH, GroupBy.YEAR)) {
            EarningsReportResponse result = reportService.earnings(EMAIL, ALL, groupBy);

            assertThat(result.buckets()).hasSize(2);
            assertThat(result.buckets().getLast().key()).isEqualTo("unknown");
            assertThat(result.buckets().getLast().label()).isEqualTo("Unknown date");
            assertThat(result.buckets().getLast().periodStart()).isNull();
        }
    }

    private Shift shift(String station, LocalDate date, String base, String tips, int minutes) {
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = start.plusMinutes(minutes);
        return new Shift(1L, station, date, start, end, new BigDecimal(base),
                tips == null ? null : new BigDecimal(tips));
    }

    @Test
    void statistics_leaveScheduledBlocksOutAndCountCancellationPayWithoutHours() {
        Shift completed = shift("VEA7", LocalDate.of(2026, 9, 6), "100.00", "20.00", 240);
        Shift cancelled = withStatus(shift("VEA7", LocalDate.of(2026, 9, 7), "18.00", "0.00", 240), ShiftStatus.CANCELLED);
        Shift scheduled = withStatus(shift("VEA7", LocalDate.of(2026, 9, 20), "84.00", "0.00", 240), ShiftStatus.SCHEDULED);
        when(shiftService.findFiltered(EMAIL, ALL)).thenReturn(List.of(completed, cancelled, scheduled));

        ShiftStatisticsResponse result = reportService.statistics(EMAIL, ALL);

        assertThat(result.getTotalEarnings()).isEqualByComparingTo("138.00");
        assertThat(result.getTotalBasePay()).isEqualByComparingTo("118.00");
        assertThat(result.getTotalTips()).isEqualByComparingTo("20.00");
        assertThat(result.getTotalTimeWorked()).isEqualTo(240);
        assertThat(result.getAverageShiftMinutes()).isEqualTo(240);
    }

    @Test
    void statistics_countCancellationsAndForfeitsInTheRangeAndThisMonth() {
        Shift cancelled = withStatus(shift("VEA7", LocalDate.of(2026, 8, 7), "18.00", "0.00", 240), ShiftStatus.CANCELLED);
        Shift forfeited = withStatus(shift("VEA7", LocalDate.of(2026, 8, 9), "0.00", "0.00", 240), ShiftStatus.FORFEITED);
        Shift forfeitedThisMonth = withStatus(shift("VEA7", LocalDate.of(2026, 9, 9), "0.00", "0.00", 240), ShiftStatus.FORFEITED);
        org.mockito.Mockito.lenient().when(shiftService.findFiltered(EMAIL, ALL.withStatuses(Set.of(ShiftStatus.CANCELLED, ShiftStatus.FORFEITED))))
                .thenReturn(List.of(cancelled, forfeited, forfeitedThisMonth));
        org.mockito.Mockito.lenient().when(shiftService.findFiltered(EMAIL, ShiftFilter.report(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30), null, null)
                .withStatuses(Set.of(ShiftStatus.FORFEITED)))).thenReturn(List.of(forfeitedThisMonth));

        ShiftStatisticsResponse result = reportService.statistics(EMAIL, ALL);

        assertThat(result.getCancelledShifts()).isEqualTo(1);
        assertThat(result.getForfeitedShifts()).isEqualTo(2);
        assertThat(result.getForfeitedThisMonth()).isEqualTo(1);
    }

    @Test
    void statistics_planTheNextSevenDaysInTheDriversTimeZone() {
        // 03:00 UTC on Sep 12 is still 20:00 on Sep 11 in Los Angeles.
        AppUserRepository users = org.mockito.Mockito.mock(AppUserRepository.class);
        AppUser driver = new AppUser("Angel", EMAIL, "hash");
        driver.setTimeZone("America/Los_Angeles");
        when(users.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(driver));
        ShiftReportService service = new ShiftReportService(shiftService, expenseService, settingsService, calculator,
                new UserTimeService(users, Clock.fixed(Instant.parse("2026-09-12T03:00:00Z"), ZoneOffset.UTC)), new GoalProgressCalculator(),
                new PayPeriodCalculator(), deposits);
        Shift missed = withStatus(shift("VEA7", LocalDate.of(2026, 9, 11), "70.00", "0.00", 240), ShiftStatus.SCHEDULED);
        Shift tonight = withStatus(new Shift(2L, "VEA7", LocalDate.of(2026, 9, 11), LocalTime.of(21, 0),
                LocalTime.of(0, 0), new BigDecimal("60.00"), BigDecimal.ZERO), ShiftStatus.SCHEDULED);
        Shift nextWeek = withStatus(shift("VEA7", LocalDate.of(2026, 9, 17), "80.00", "0.00", 240), ShiftStatus.SCHEDULED);
        when(shiftService.findScheduled(EMAIL, LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 17)))
                .thenReturn(List.of(missed, tonight, nextWeek));
        when(shiftService.findScheduled(EMAIL, null, LocalDate.of(2026, 9, 11))).thenReturn(List.of(missed, tonight));

        ShiftStatisticsResponse result = service.statistics(EMAIL, ALL);

        assertThat(result.getScheduledShifts()).isEqualTo(2);
        assertThat(result.getScheduledMinutes()).isEqualTo(420);
        assertThat(result.getExpectedPay()).isEqualByComparingTo("140.00");
        assertThat(result.getNeedsConfirmation()).isEqualTo(1);
    }

    private Shift withStatus(Shift shift, ShiftStatus status) {
        shift.setStatus(status);
        return shift;
    }

    @Test
    void statistics_useActualTimesForTheClockedRateAndLeaveUntimedBlocksAsScheduled() {
        Shift timed = shift("VEA7", LocalDate.of(2026, 9, 6), "84.00", "0.00", 240);
        timed.setActualStart(LocalTime.of(8, 20));
        timed.setActualEnd(LocalTime.of(11, 42));
        Shift untimed = shift("VEA7", LocalDate.of(2026, 9, 7), "84.00", "0.00", 240);
        when(shiftService.findFiltered(EMAIL, ALL)).thenReturn(List.of(timed, untimed));

        ShiftStatisticsResponse statistics = reportService.statistics(EMAIL, ALL);

        assertThat(statistics.getTotalTimeWorked()).isEqualTo(480);
        assertThat(statistics.getAverageHourlyEarnings()).isEqualByComparingTo("21.00");
        assertThat(statistics.getTimedShifts()).isEqualTo(1);
        assertThat(statistics.getClockedMinutes()).isEqualTo(442);
        assertThat(statistics.getClockedHourlyRate()).isEqualByComparingTo("22.81");
        assertThat(statistics.getAverageFinishedEarlyMinutes()).isEqualTo(38);
    }

    @Test
    void statistics_reportNoEarlyFinishWhenNoBlockIsTimed() {
        when(shiftService.findFiltered(EMAIL, ALL)).thenReturn(List.of(
                shift("VEA7", LocalDate.of(2026, 9, 6), "84.00", "0.00", 240)));

        ShiftStatisticsResponse statistics = reportService.statistics(EMAIL, ALL);

        assertThat(statistics.getTimedShifts()).isZero();
        assertThat(statistics.getClockedMinutes()).isEqualTo(240);
        assertThat(statistics.getClockedHourlyRate()).isEqualByComparingTo(statistics.getAverageHourlyEarnings());
        assertThat(statistics.getAverageFinishedEarlyMinutes()).isNull();
    }

    @Test
    void earningsByStation_reportsHowEarlyEachStationFinishes() {
        Shift early = shift("VEA7", LocalDate.of(2026, 9, 6), "84.00", "0.00", 240);
        early.setActualStart(LocalTime.of(8, 0));
        early.setActualEnd(LocalTime.of(11, 20));
        Shift over = shift("DAX5", LocalDate.of(2026, 9, 7), "84.00", "0.00", 240);
        over.setActualStart(LocalTime.of(8, 0));
        over.setActualEnd(LocalTime.of(12, 15));
        when(shiftService.findFiltered(EMAIL, ALL)).thenReturn(List.of(early, over));

        EarningsReportResponse report = reportService.earnings(EMAIL, ALL, GroupBy.STATION);

        assertThat(report.buckets()).extracting(EarningsBucket::label, EarningsBucket::averageFinishedEarlyMinutes)
                .containsExactlyInAnyOrder(org.assertj.core.groups.Tuple.tuple("VEA7", 40),
                        org.assertj.core.groups.Tuple.tuple("DAX5", -15));
    }

    @Test
    void earningsByStation_averageRouteCountsOnlyOverBlocksThatHaveThem() {
        Shift counted = shift("VEA7", LocalDate.of(2026, 9, 6), "84.00", "0.00", 240);
        counted.setStops(40);
        counted.setPackages(80);
        counted.setReturns(2);
        Shift alsoCounted = shift("VEA7", LocalDate.of(2026, 9, 7), "84.00", "0.00", 240);
        alsoCounted.setStops(60);
        alsoCounted.setReturns(3);
        Shift uncounted = shift("VEA7", LocalDate.of(2026, 9, 8), "84.00", "0.00", 240);
        when(shiftService.findFiltered(EMAIL, ALL)).thenReturn(List.of(counted, alsoCounted, uncounted));

        EarningsReportResponse report = reportService.earnings(EMAIL, ALL, GroupBy.STATION);

        assertThat(report.buckets()).singleElement().satisfies(bucket -> {
            assertThat(bucket.shifts()).isEqualTo(3);
            assertThat(bucket.shiftsWithRouteData()).isEqualTo(2);
            assertThat(bucket.averageStops()).isEqualByComparingTo("50.0");
            // 480 minutes over 100 stops; 5 returns out of 80 packages plus 60 stops.
            assertThat(bucket.averageMinutesPerStop()).isEqualByComparingTo("4.8");
            assertThat(bucket.returnsRate()).isEqualByComparingTo("3.6");
        });
    }

    @Test
    void statistics_countLateForfeitsInTheRangeAndThisMonth() {
        Shift late = withStatus(shift("VEA7", LocalDate.of(2026, 9, 5), "0.00", "0.00", 240), ShiftStatus.FORFEITED);
        late.setLateForfeit(true);
        Shift onTime = withStatus(shift("VEA7", LocalDate.of(2026, 9, 6), "0.00", "0.00", 240), ShiftStatus.FORFEITED);
        Shift lastMonth = withStatus(shift("VEA7", LocalDate.of(2026, 8, 30), "0.00", "0.00", 240), ShiftStatus.FORFEITED);
        lastMonth.setLateForfeit(true);
        List<Shift> all = List.of(late, onTime, lastMonth);
        when(shiftService.findFiltered(org.mockito.ArgumentMatchers.eq(EMAIL), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    ShiftFilter filter = invocation.getArgument(1);
                    return all.stream()
                            .filter(shift -> filter.statuses().contains(shift.getStatus()))
                            .filter(shift -> filter.from() == null || !shift.getDate().isBefore(filter.from()))
                            .filter(shift -> filter.to() == null || !shift.getDate().isAfter(filter.to()))
                            .toList();
                });

        ShiftStatisticsResponse statistics = reportService.statistics(EMAIL, ALL);

        assertThat(statistics.getForfeitedShifts()).isEqualTo(3);
        assertThat(statistics.getLateForfeitedShifts()).isEqualTo(2);
        assertThat(statistics.getForfeitedThisMonth()).isEqualTo(2);
        assertThat(statistics.getLateForfeitedThisMonth()).isEqualTo(1);
    }

    @Test
    void goals_countTheWeekAndMonthOnTheChosenBasis() {
        // NOW is Saturday, September 12, so the week is September 7 to 13.
        Shift monday = shift("VEA7", LocalDate.of(2026, 9, 7), "100.00", "20.00", 240);
        monday.setMiles(new BigDecimal("30.0"));
        Shift lastWeek = shift("VEA7", LocalDate.of(2026, 9, 3), "80.00", "0.00", 240);
        Shift sunday = withStatus(shift("VEA7", LocalDate.of(2026, 9, 13), "90.00", "0.00", 240), ShiftStatus.SCHEDULED);
        List<Shift> all = List.of(monday, lastWeek, sunday);
        when(shiftService.findFiltered(org.mockito.ArgumentMatchers.eq(EMAIL), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    ShiftFilter filter = invocation.getArgument(1);
                    return all.stream()
                            .filter(shift -> filter.statuses().contains(shift.getStatus()))
                            .filter(shift -> !shift.getDate().isBefore(filter.from()) && !shift.getDate().isAfter(filter.to()))
                            .toList();
                });
        when(shiftService.findScheduled(org.mockito.ArgumentMatchers.eq(EMAIL), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenReturn(List.of(sunday));
        when(userTime.today(EMAIL)).thenReturn(NOW.toLocalDate());

        when(settingsService.get(EMAIL)).thenReturn(goalSettings("600.00", null, com.angel.flexbuddy.model.GoalBasis.GROSS));
        com.angel.flexbuddy.dto.GoalsResponse gross = reportService.goals(EMAIL);
        assertThat(gross.month()).isNull();
        assertThat(gross.week().earned()).isEqualByComparingTo("120.00");
        assertThat(gross.week().planned()).isEqualByComparingTo("90.00");
        assertThat(gross.week().averageBlockPay()).isEqualByComparingTo("100.00");
        assertThat(gross.week().averageBlockMinutes()).isEqualTo(240);

        when(settingsService.get(EMAIL)).thenReturn(goalSettings("600.00", "2000.00", com.angel.flexbuddy.model.GoalBasis.NET));
        com.angel.flexbuddy.dto.GoalsResponse net = reportService.goals(EMAIL);
        // Monday nets $120 - 30 mi x $0.70 = $99; over 90 days $200 gross nets $179, so $90 scheduled counts as $80.55.
        assertThat(net.week().earned()).isEqualByComparingTo("99.00");
        assertThat(net.week().planned()).isEqualByComparingTo("80.55");
        assertThat(net.week().averageBlockPay()).isEqualByComparingTo("89.50");
        assertThat(net.month().goal()).isEqualByComparingTo("2000.00");
        assertThat(net.month().earned()).isEqualByComparingTo("179.00");
    }

    @Test
    void goals_areEmptyWithoutAnyGoalSet() {
        when(settingsService.get(EMAIL)).thenReturn(goalSettings(null, null, com.angel.flexbuddy.model.GoalBasis.GROSS));

        com.angel.flexbuddy.dto.GoalsResponse goals = reportService.goals(EMAIL);

        assertThat(goals.week()).isNull();
        assertThat(goals.month()).isNull();
        org.mockito.Mockito.verifyNoInteractions(shiftService);
    }

    private static com.angel.flexbuddy.dto.AccountSettingsResponse goalSettings(String weekly, String monthly,
            com.angel.flexbuddy.model.GoalBasis basis) {
        return new com.angel.flexbuddy.dto.AccountSettingsResponse(com.angel.flexbuddy.model.VehicleCostMethod.STANDARD_MILEAGE,
                new BigDecimal("0.70"), new BigDecimal("0.70"), 2025, "America/New_York", null, false, false, 45,
                weekly == null ? null : new BigDecimal(weekly), monthly == null ? null : new BigDecimal(monthly), basis,
                List.of(java.time.DayOfWeek.TUESDAY, java.time.DayOfWeek.FRIDAY), 1, null, null);
    }

    @Test
    void payPeriods_totalEarnedAndScheduledPayForEachPeriod() {
        // NOW is Saturday, September 12: its blocks are paid Tuesday the 15th, for September 11 to 14.
        Shift friday = shift("VEA7", LocalDate.of(2026, 9, 11), "80.00", "10.00", 240);
        Shift cancelled = withStatus(shift("VEA7", LocalDate.of(2026, 9, 12), "18.00", "0.00", 240), ShiftStatus.CANCELLED);
        Shift sunday = withStatus(shift("VEA7", LocalDate.of(2026, 9, 13), "84.00", "0.00", 240), ShiftStatus.SCHEDULED);
        Shift wednesday = shift("VEA7", LocalDate.of(2026, 9, 9), "70.00", "0.00", 240);
        List<Shift> all = List.of(friday, cancelled, sunday, wednesday);
        when(shiftService.findFiltered(org.mockito.ArgumentMatchers.eq(EMAIL), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    ShiftFilter filter = invocation.getArgument(1);
                    return all.stream().filter(shift -> filter.statuses().contains(shift.getStatus()))
                            .filter(shift -> !shift.getDate().isBefore(filter.from()) && !shift.getDate().isAfter(filter.to()))
                            .toList();
                });
        when(shiftService.findScheduled(org.mockito.ArgumentMatchers.eq(EMAIL), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> {
                    LocalDate from = invocation.getArgument(1);
                    LocalDate to = invocation.getArgument(2);
                    return all.stream().filter(shift -> shift.getStatus() == ShiftStatus.SCHEDULED)
                            .filter(shift -> !shift.getDate().isBefore(from) && !shift.getDate().isAfter(to)).toList();
                });
        when(userTime.today(EMAIL)).thenReturn(NOW.toLocalDate());

        com.angel.flexbuddy.dto.PayPeriodsResponse periods = reportService.payPeriods(EMAIL, 2);

        assertThat(periods.periods()).hasSize(2);
        com.angel.flexbuddy.dto.PayPeriodResponse current = periods.periods().getFirst();
        assertThat(current.payoutDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThat(current.from()).isEqualTo(LocalDate.of(2026, 9, 11));
        assertThat(current.to()).isEqualTo(LocalDate.of(2026, 9, 14));
        assertThat(current.blocks()).isEqualTo(2);
        assertThat(current.earned()).isEqualByComparingTo("108.00");
        assertThat(current.scheduledBlocks()).isEqualTo(1);
        assertThat(current.scheduledPay()).isEqualByComparingTo("84.00");
        com.angel.flexbuddy.dto.PayPeriodResponse last = periods.periods().get(1);
        assertThat(last.payoutDate()).isEqualTo(LocalDate.of(2026, 9, 11));
        assertThat(last.earned()).isEqualByComparingTo("70.00");
        assertThat(periods.nextPayout()).isEqualTo(current);
    }

    @Test
    void payPeriods_compareWhatLandedWithWhatTheBlocksEarned() {
        // NOW is Saturday, September 12. Payouts: Tue 15th (upcoming), Fri 11th, Tue 8th, Fri 4th.
        List<Shift> all = List.of(
                shift("VEA7", LocalDate.of(2026, 9, 9), "70.00", "0.00", 240),     // paid Fri 11th
                shift("VEA7", LocalDate.of(2026, 9, 5), "50.00", "10.00", 180),    // paid Tue 8th
                shift("VEA7", LocalDate.of(2026, 9, 1), "72.00", "0.00", 240),     // paid Fri 4th
                shift("VEA7", LocalDate.of(2026, 9, 12), "84.00", "0.00", 240));   // paid Tue 15th
        when(shiftService.findFiltered(org.mockito.ArgumentMatchers.eq(EMAIL), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    ShiftFilter filter = invocation.getArgument(1);
                    return all.stream()
                            .filter(shift -> !shift.getDate().isBefore(filter.from()) && !shift.getDate().isAfter(filter.to()))
                            .toList();
                });
        when(userTime.today(EMAIL)).thenReturn(NOW.toLocalDate());
        when(deposits.findByOwnerEmailIgnoreCaseAndPayoutDateBetween(EMAIL, LocalDate.of(2026, 9, 4), LocalDate.of(2026, 9, 15)))
                .thenReturn(List.of(deposit(LocalDate.of(2026, 9, 11), "65.00", "Chase"),
                        deposit(LocalDate.of(2026, 9, 8), "72.50", null)));

        List<com.angel.flexbuddy.dto.PayPeriodResponse> periods = reportService.payPeriods(EMAIL, 4).periods();

        assertThat(periods).extracting(com.angel.flexbuddy.dto.PayPeriodResponse::payoutDate).containsExactly(
                LocalDate.of(2026, 9, 15), LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 8), LocalDate.of(2026, 9, 4));
        assertThat(periods).extracting(com.angel.flexbuddy.dto.PayPeriodResponse::status).containsExactly(
                com.angel.flexbuddy.dto.PayoutStatus.UPCOMING, com.angel.flexbuddy.dto.PayoutStatus.SHORT,
                com.angel.flexbuddy.dto.PayoutStatus.OVER, com.angel.flexbuddy.dto.PayoutStatus.UNCHECKED);
        com.angel.flexbuddy.dto.PayPeriodResponse friday = periods.get(1);
        assertThat(friday.received()).isEqualByComparingTo("65.00");
        assertThat(friday.difference()).isEqualByComparingTo("-5.00");
        assertThat(friday.note()).isEqualTo("Chase");
        assertThat(periods.get(2).difference()).isEqualByComparingTo("12.50");
        assertThat(periods.get(3).received()).isNull();
        assertThat(periods.get(3).difference()).isNull();
    }

    @Test
    void payPeriods_anAmountWithinACentOfTheEarnedPayMatches() {
        when(shiftService.findFiltered(org.mockito.ArgumentMatchers.eq(EMAIL), org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    ShiftFilter filter = invocation.getArgument(1);
                    Shift wednesday = shift("VEA7", LocalDate.of(2026, 9, 9), "70.00", "0.00", 240);
                    return filter.from().equals(LocalDate.of(2026, 9, 8)) ? List.of(wednesday) : List.<Shift>of();
                });
        when(userTime.today(EMAIL)).thenReturn(NOW.toLocalDate());
        when(deposits.findByOwnerEmailIgnoreCaseAndPayoutDateBetween(org.mockito.ArgumentMatchers.eq(EMAIL),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(deposit(LocalDate.of(2026, 9, 11), "70.00", null)));

        com.angel.flexbuddy.dto.PayPeriodResponse friday = reportService.payPeriods(EMAIL, 2).periods().get(1);

        assertThat(friday.status()).isEqualTo(com.angel.flexbuddy.dto.PayoutStatus.MATCHED);
        assertThat(friday.difference()).isEqualByComparingTo("0.00");
    }

    private static com.angel.flexbuddy.model.PayoutDeposit deposit(LocalDate payoutDate, String amount, String note) {
        com.angel.flexbuddy.model.PayoutDeposit deposit = new com.angel.flexbuddy.model.PayoutDeposit();
        deposit.setPayoutDate(payoutDate);
        deposit.setAmount(new BigDecimal(amount));
        deposit.setNote(note);
        return deposit;
    }

    @Test
    void payPeriods_rejectsACountOutsideHalfAYear() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> reportService.payPeriods(EMAIL, 27))
                .isInstanceOf(com.angel.flexbuddy.exception.InvalidFilterException.class);
    }

    @Test
    void heatmap_groupsWorkedBlocksByWeekdayAndStartBandAndNamesTheBestFullCell() {
        List<Shift> blocks = new java.util.ArrayList<>();
        // Three Saturday 3 pm blocks at $96 for 4 hours, and two Monday 7 am blocks at $72.
        for (int week = 0; week < 3; week++) {
            Shift saturday = new Shift((long) blocks.size() + 1, "VEA7", LocalDate.of(2026, 8, 29).plusWeeks(week),
                    LocalTime.of(15, 0), LocalTime.of(19, 0), new BigDecimal("96.00"), BigDecimal.ZERO);
            blocks.add(saturday);
        }
        for (int week = 0; week < 2; week++) {
            blocks.add(new Shift((long) blocks.size() + 1, "VEA7", LocalDate.of(2026, 8, 31).plusWeeks(week),
                    LocalTime.of(7, 59), LocalTime.of(11, 59), new BigDecimal("72.00"), BigDecimal.ZERO));
        }
        // One lucky Sunday evening block pays far more but stays sparse.
        blocks.add(new Shift(99L, "VEA7", LocalDate.of(2026, 9, 6), LocalTime.of(18, 0), LocalTime.of(20, 0),
                new BigDecimal("150.00"), BigDecimal.ZERO));
        when(shiftService.findFiltered(org.mockito.ArgumentMatchers.eq(EMAIL), org.mockito.ArgumentMatchers.any()))
                .thenReturn(blocks);
        when(expenseService.findForShifts(org.mockito.ArgumentMatchers.eq(EMAIL), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        com.angel.flexbuddy.dto.HeatmapResponse heatmap = reportService.heatmap(EMAIL, ALL,
                com.angel.flexbuddy.dto.HeatmapMetric.GROSS_HOURLY);

        assertThat(heatmap.totalShifts()).isEqualTo(6);
        assertThat(heatmap.cells()).extracting(cell -> cell.weekday() + "/" + cell.band() + "/" + cell.shifts() + "/" + cell.sparse())
                .containsExactlyInAnyOrder("1/0/2/false", "6/3/3/false", "7/4/1/true");
        assertThat(heatmap.best().weekday()).isEqualTo(6);
        assertThat(heatmap.best().value()).isEqualByComparingTo("24.00");
        assertThat(heatmap.scale()).extracting(BigDecimal::toPlainString).containsExactly("18.00", "24.00");
    }

    @Test
    void heatmap_withNoBlocksHasNoBestAndNoScale() {
        when(shiftService.findFiltered(org.mockito.ArgumentMatchers.eq(EMAIL), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of());

        com.angel.flexbuddy.dto.HeatmapResponse heatmap = reportService.heatmap(EMAIL, ALL,
                com.angel.flexbuddy.dto.HeatmapMetric.NET_HOURLY);

        assertThat(heatmap.cells()).isEmpty();
        assertThat(heatmap.best()).isNull();
        assertThat(heatmap.scale()).isEmpty();
        assertThat(heatmap.bands()).hasSize(5);
    }

    @Test
    void spanishReportsTranslateLabelsWithoutChangingBucketKeys() {
        org.springframework.context.i18n.LocaleContextHolder.setLocale(java.util.Locale.forLanguageTag("es"));
        try {
            when(shiftService.findFiltered(EMAIL, ALL))
                    .thenReturn(List.of(shift("VEA7", LocalDate.of(2026, 9, 7), "120.00", "0", 240)));
            var weeks = reportService.earnings(EMAIL, ALL, GroupBy.WEEK);
            assertThat(weeks.buckets()).singleElement().satisfies(bucket -> {
                assertThat(bucket.key()).isEqualTo("2026-W37");
                assertThat(bucket.label()).isEqualTo("Semana del 7 de sept");
            });
            assertThat(reportService.earnings(EMAIL, ALL, GroupBy.MONTH).buckets().getFirst().label())
                    .isEqualTo("sept 2026");
            assertThat(reportService.heatmap(EMAIL, ALL, com.angel.flexbuddy.dto.HeatmapMetric.NET_HOURLY).bands())
                    .containsExactly("Antes de las 8 a. m.", "8–11 a. m.", "11 a. m.–2 p. m.",
                            "2–5 p. m.", "Después de las 5 p. m.");
        } finally {
            org.springframework.context.i18n.LocaleContextHolder.resetLocaleContext();
        }
    }

}
