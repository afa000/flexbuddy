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
                new UserTimeService(users, Clock.fixed(Instant.parse("2026-09-12T03:00:00Z"), ZoneOffset.UTC)));
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
}
