package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.TaxQuarterResponse;
import com.angel.flexbuddy.dto.TaxSummaryResponse;
import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.model.GoalBasis;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.TaxPayment;
import com.angel.flexbuddy.model.VehicleCostMethod;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.TaxPaymentRepository;

@ExtendWith(MockitoExtension.class)
class TaxServiceTest {

    private static final String EMAIL = "angel@example.com";
    // A Thursday in the third estimated-tax period.
    private static final LocalDate TODAY = LocalDate.of(2026, 9, 10);

    @Mock ShiftService shiftService;
    @Mock ExpenseService expenseService;
    @Mock AccountSettingsService settingsService;
    @Mock UserTimeService userTime;
    @Mock TaxPaymentRepository payments;
    @Mock AppUserRepository userRepository;

    private final List<Shift> shifts = new ArrayList<>();
    private final List<TaxPayment> recorded = new ArrayList<>();
    private TaxService service;

    @BeforeEach
    void setUp() {
        service = new TaxService(shiftService, expenseService, settingsService, new NetEarningsCalculator(), userTime,
                payments, userRepository, Clock.fixed(Instant.parse("2026-09-10T12:00:00Z"), ZoneOffset.UTC),
                "04-15,06-15,09-15,01-15");
        lenient().when(userTime.today(EMAIL)).thenReturn(TODAY);
        lenient().when(expenseService.findFiltered(eq(EMAIL), any())).thenReturn(List.of());
        lenient().when(payments.findByOwnerEmailIgnoreCaseAndTaxYearOrderByPaidOnDescIdDesc(EMAIL, 2026)).thenReturn(recorded);
        lenient().when(shiftService.findFiltered(eq(EMAIL), any(ShiftFilter.class))).thenAnswer(invocation -> {
            ShiftFilter filter = invocation.getArgument(1);
            return shifts.stream().filter(shift -> filter.statuses().contains(shift.getStatus()))
                    .filter(shift -> !shift.getDate().isBefore(filter.from()) && !shift.getDate().isAfter(filter.to()))
                    .toList();
        });
        useRate(new BigDecimal("25"));
    }

    @Test
    void thisWeeksSetAsideIsTheChosenShareOfItsNet() {
        block(LocalDate.of(2026, 9, 7), "150.00", "0.00");
        block(LocalDate.of(2026, 9, 9), "122.00", "0.00");

        TaxSummaryResponse summary = service.summary(EMAIL, 2026);

        assertThat(summary.thisWeekNet()).isEqualByComparingTo("272.00");
        assertThat(summary.thisWeekSetAside()).isEqualByComparingTo("68.00");
    }

    @Test
    void aRecordedPaymentReducesOnlyWhatIsStillToSetAside() {
        block(LocalDate.of(2026, 2, 10), "4000.00", "0.00");
        TaxSummaryResponse before = service.summary(EMAIL, 2026);
        recorded.add(payment(1, "500.00"));

        TaxSummaryResponse after = service.summary(EMAIL, 2026);

        assertThat(before.reserveToDate()).isEqualByComparingTo("1000.00");
        assertThat(before.remaining()).isEqualByComparingTo("1000.00");
        assertThat(after.reserveToDate()).isEqualByComparingTo("1000.00");
        assertThat(after.paid()).isEqualByComparingTo("500.00");
        assertThat(after.remaining()).isEqualByComparingTo("500.00");
        assertThat(after.quarters().getFirst().paid()).isEqualByComparingTo("500.00");
    }

    @Test
    void quartersFollowTheEstimatedTaxPeriodsAndTheirDueDates() {
        block(LocalDate.of(2026, 3, 31), "100.00", "0.00");
        block(LocalDate.of(2026, 4, 1), "200.00", "0.00");
        block(LocalDate.of(2026, 5, 31), "300.00", "0.00");
        block(LocalDate.of(2026, 6, 1), "400.00", "0.00");

        List<TaxQuarterResponse> quarters = service.summary(EMAIL, 2026).quarters();

        assertThat(quarters).extracting(TaxQuarterResponse::dueDate).containsExactly(LocalDate.of(2026, 4, 15),
                LocalDate.of(2026, 6, 15), LocalDate.of(2026, 9, 15), LocalDate.of(2027, 1, 15));
        assertThat(quarters).extracting(quarter -> quarter.net().toPlainString())
                .containsExactly("100.00", "500.00", "400.00", "0.00");
        assertThat(quarters.get(1).from()).isEqualTo(LocalDate.of(2026, 4, 1));
        assertThat(quarters.get(1).to()).isEqualTo(LocalDate.of(2026, 5, 31));
        assertThat(quarters.get(3).to()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void theNextDueDateIsTheFirstOneNotYetPassed() {
        assertThat(service.summary(EMAIL, 2026).nextDueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        when(userTime.today(EMAIL)).thenReturn(LocalDate.of(2026, 9, 16));
        assertThat(service.summary(EMAIL, 2026).nextDueDate()).isEqualTo(LocalDate.of(2027, 1, 15));
        when(userTime.today(EMAIL)).thenReturn(LocalDate.of(2027, 1, 16));
        assertThat(service.summary(EMAIL, 2026).nextDueDate()).isNull();
    }

    @Test
    void withoutAChosenPercentageOnlyTheNetIsShown() {
        useRate(null);
        block(LocalDate.of(2026, 9, 8), "100.00", "0.00");

        TaxSummaryResponse summary = service.summary(EMAIL, 2026);

        assertThat(summary.netYearToDate()).isEqualByComparingTo("100.00");
        assertThat(summary.reserveToDate()).isNull();
        assertThat(summary.remaining()).isNull();
        assertThat(summary.thisWeekSetAside()).isNull();
    }

    @Test
    void aYearWithNoBlocksOrALossSetsNothingAside() {
        TaxSummaryResponse empty = service.summary(EMAIL, 2026);

        assertThat(empty.netYearToDate()).isEqualByComparingTo("0.00");
        assertThat(empty.reserveToDate()).isEqualByComparingTo("0.00");
        assertThat(empty.nextDueDate()).isEqualTo(LocalDate.of(2026, 9, 15));
        assertThatThrownBy(() -> service.summary(EMAIL, 1999)).isInstanceOf(InvalidFilterException.class);
    }

    @Test
    void theYearCsvHasAMonthPerRowAndAYearTotalThatAddsUp() throws Exception {
        Shift february = block(LocalDate.of(2026, 2, 10), "100.00", "20.00");
        february.setMiles(new BigDecimal("30.0"));
        block(LocalDate.of(2026, 9, 8), "80.00", "0.00");
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        service.writeYearCsv(EMAIL, 2026, output);

        String csv = new String(output.toByteArray(), 3, output.size() - 3, StandardCharsets.UTF_8);
        String[] lines = csv.split("\r\n");
        assertThat(lines).hasSize(14);
        assertThat(lines[0]).startsWith("month,blocks,base_pay,tips,gross,miles,mileage_rate,mileage_cost,");
        assertThat(lines[2]).isEqualTo("2026-02,1,100.00,20.00,120.00,30.0,0.70,21.00,0.00,0.00,0.00,0.00,0.00,"
                + "STANDARD_MILEAGE,21.00,21.00,99.00");
        assertThat(lines[13]).startsWith("2026,2,180.00,20.00,200.00,30.0,").endsWith(",179.00");
    }

    private void useRate(BigDecimal percent) {
        lenient().when(settingsService.get(EMAIL)).thenReturn(new AccountSettingsResponse(VehicleCostMethod.STANDARD_MILEAGE,
                new BigDecimal("0.70"), new BigDecimal("0.70"), 2025, "America/New_York", null, false, false, 45, null,
                null, GoalBasis.GROSS, List.of(DayOfWeek.TUESDAY, DayOfWeek.FRIDAY), 1, percent, null));
    }

    private Shift block(LocalDate date, String base, String tips) {
        Shift shift = new Shift((long) shifts.size() + 1, "VEA7", date, LocalTime.of(9, 0), LocalTime.of(13, 0),
                new BigDecimal(base), new BigDecimal(tips));
        shifts.add(shift);
        return shift;
    }

    private static TaxPayment payment(Integer quarter, String amount) {
        TaxPayment payment = new TaxPayment();
        payment.setTaxYear(2026);
        payment.setQuarter(quarter);
        payment.setPaidOn(LocalDate.of(2026, 4, 14));
        payment.setAmount(new BigDecimal(amount));
        return payment;
    }
}
