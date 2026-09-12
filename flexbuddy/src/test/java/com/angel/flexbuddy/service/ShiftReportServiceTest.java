package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.EarningsReportResponse;
import com.angel.flexbuddy.dto.GroupBy;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.ShiftStatisticsResponse;
import com.angel.flexbuddy.model.Shift;

@ExtendWith(MockitoExtension.class)
class ShiftReportServiceTest {

    private static final String EMAIL = "angel@example.com";
    private static final ShiftFilter ALL = ShiftFilter.report(null, null, null, null);

    @Mock ShiftService shiftService;
    @InjectMocks ShiftReportService reportService;

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

    private Shift shift(String station, LocalDate date, String base, String tips, int minutes) {
        LocalTime start = LocalTime.of(8, 0);
        LocalTime end = start.plusMinutes(minutes);
        return new Shift(1L, station, date, start, end, new BigDecimal(base),
                tips == null ? null : new BigDecimal(tips));
    }
}

