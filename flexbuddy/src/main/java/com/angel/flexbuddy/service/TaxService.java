package com.angel.flexbuddy.service;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.dto.ExpenseFilter;
import com.angel.flexbuddy.dto.NetEarningsResult;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.TaxPaymentRequest;
import com.angel.flexbuddy.dto.TaxPaymentResponse;
import com.angel.flexbuddy.dto.TaxQuarterResponse;
import com.angel.flexbuddy.dto.TaxSummaryResponse;
import com.angel.flexbuddy.exception.InvalidFilterException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.TaxPayment;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.TaxPaymentRepository;

/**
 * Estimated tax reserve: plain arithmetic on the driver's own numbers and the one percentage they chose. Quarters
 * follow the US federal estimated-payment periods; the due dates are configuration so a moved deadline is a
 * property change.
 */
@Service
public class TaxService {

    // US federal estimated-tax periods: January to March, April to May, June to August, September to December.
    private static final int[] QUARTER_START_MONTHS = {1, 4, 6, 9};

    private final ShiftService shiftService;
    private final ExpenseService expenseService;
    private final AccountSettingsService settingsService;
    private final NetEarningsCalculator calculator;
    private final UserTimeService userTime;
    private final TaxPaymentRepository payments;
    private final AppUserRepository userRepository;
    private final Clock clock;
    private final List<MonthDay> dueDates;

    public TaxService(ShiftService shiftService, ExpenseService expenseService, AccountSettingsService settingsService,
            NetEarningsCalculator calculator, UserTimeService userTime, TaxPaymentRepository payments,
            AppUserRepository userRepository, Clock clock,
            @Value("${flexbuddy.tax.due-dates:04-15,06-15,09-15,01-15}") String dueDates) {
        this.shiftService = shiftService;
        this.expenseService = expenseService;
        this.settingsService = settingsService;
        this.calculator = calculator;
        this.userTime = userTime;
        this.payments = payments;
        this.userRepository = userRepository;
        this.clock = clock;
        this.dueDates = Arrays.stream(dueDates.split(",")).map(String::trim)
                .map(value -> MonthDay.parse("--" + value)).toList();
        if (this.dueDates.size() != 4) throw new IllegalArgumentException("flexbuddy.tax.due-dates needs four dates.");
    }

    @Transactional(readOnly = true)
    public TaxSummaryResponse summary(String email, int year) {
        checkYear(year);
        AccountSettingsResponse settings = settingsService.get(email);
        BigDecimal percent = settings.taxSetAsidePercent();
        LocalDate today = userTime.today(email);
        List<TaxPayment> recorded = payments.findByOwnerEmailIgnoreCaseAndTaxYearOrderByPaidOnDescIdDesc(email, year);

        List<TaxQuarterResponse> quarters = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            LocalDate from = LocalDate.of(year, QUARTER_START_MONTHS[index], 1);
            LocalDate to = index == 3 ? LocalDate.of(year, 12, 31) : LocalDate.of(year, QUARTER_START_MONTHS[index + 1], 1).minusDays(1);
            BigDecimal net = net(email, from, to, settings).netEarnings();
            int quarter = index + 1;
            BigDecimal paid = sum(recorded.stream().filter(payment -> Integer.valueOf(quarter).equals(payment.getQuarter())).toList());
            quarters.add(new TaxQuarterResponse(quarter, from, to, dueDate(year, index), net, setAside(net, percent), paid));
        }

        BigDecimal netYear = net(email, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), settings).netEarnings();
        BigDecimal reserve = setAside(netYear, percent);
        BigDecimal paid = sum(recorded);
        LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        BigDecimal weekNet = net(email, weekStart, weekStart.plusDays(6), settings).netEarnings();
        LocalDate nextDue = quarters.stream().map(TaxQuarterResponse::dueDate).filter(date -> !date.isBefore(today))
                .findFirst().orElse(null);
        return new TaxSummaryResponse(year, percent, netYear, reserve, paid,
                reserve == null ? null : reserve.subtract(paid).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP),
                weekNet, setAside(weekNet, percent), nextDue, quarters,
                recorded.stream().map(TaxService::response).toList());
    }

    @Transactional
    public TaxPaymentResponse addPayment(String email, TaxPaymentRequest request) {
        AppUser owner = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalStateException("Signed-in account could not be found."));
        TaxPayment payment = new TaxPayment();
        payment.setOwner(owner);
        payment.setTaxYear(request.taxYear());
        payment.setQuarter(request.quarter());
        payment.setPaidOn(request.paidOn());
        payment.setAmount(request.amount().setScale(2, RoundingMode.HALF_UP));
        payment.setNote(request.note() == null || request.note().isBlank() ? null : request.note().trim());
        payment.setCreatedAt(Instant.now(clock));
        return response(payments.save(payment));
    }

    /** Removes a payment the driver recorded by mistake; it is theirs to add again. */
    @Transactional
    public void deletePayment(String email, Long id) {
        TaxPayment payment = payments.findByIdAndOwnerEmailIgnoreCase(id, email)
                .orElseThrow(() -> new com.angel.flexbuddy.exception.TaxPaymentNotFoundException(id));
        payments.delete(payment);
    }

    private static final String[] CSV_HEADERS = {
            "month", "blocks", "base_pay", "tips", "gross", "miles", "mileage_rate", "mileage_cost", "fuel", "tolls",
            "parking", "maintenance", "other", "vehicle_cost_method", "vehicle_cost", "total_deductions", "net"
    };

    /** A month-by-month summary for a tax preparer, with a year total row, built from the same numbers as the reports. */
    @Transactional(readOnly = true)
    public void writeYearCsv(String email, int year, OutputStream output) throws IOException {
        checkYear(year);
        AccountSettingsResponse settings = settingsService.get(email);
        output.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writer.write(String.join(",", CSV_HEADERS) + "\r\n");
        for (int month = 1; month <= 12; month++) {
            YearMonth yearMonth = YearMonth.of(year, month);
            writeRow(writer, yearMonth.toString(), yearMonth.atDay(1), yearMonth.atEndOfMonth(), email, settings);
        }
        writeRow(writer, String.valueOf(year), LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31), email, settings);
        writer.flush();
    }

    private void writeRow(Writer writer, String label, LocalDate from, LocalDate to, String email,
            AccountSettingsResponse settings) throws IOException {
        List<com.angel.flexbuddy.model.Shift> shifts = shiftService.findFiltered(email, ShiftFilter.report(from, to, null, null));
        NetEarningsResult net = calculator.calculate(shifts, expenses(email, from, to), settings.vehicleCostMethod(),
                settings.mileageRate());
        BigDecimal base = shifts.stream().map(com.angel.flexbuddy.model.Shift::getEarnedBasePay).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal tips = shifts.stream().map(com.angel.flexbuddy.model.Shift::getEarnedTips).reduce(BigDecimal.ZERO, BigDecimal::add);
        writer.write(String.join(",", label, String.valueOf(shifts.size()), money(base), money(tips),
                money(net.grossEarnings()), net.miles().toPlainString(), settings.mileageRate().toPlainString(),
                money(net.mileageCost()), money(net.expenseTotals().get(ExpenseCategory.FUEL)),
                money(net.expenseTotals().get(ExpenseCategory.TOLL)), money(net.expenseTotals().get(ExpenseCategory.PARKING)),
                money(net.expenseTotals().get(ExpenseCategory.MAINTENANCE)), money(net.expenseTotals().get(ExpenseCategory.OTHER)),
                settings.vehicleCostMethod().name(), money(net.vehicleCost()), money(net.totalDeductions()),
                money(net.netEarnings())) + "\r\n");
    }

    private NetEarningsResult net(String email, LocalDate from, LocalDate to, AccountSettingsResponse settings) {
        return calculator.calculate(shiftService.findFiltered(email, ShiftFilter.report(from, to, null, null)),
                expenses(email, from, to), settings.vehicleCostMethod(), settings.mileageRate());
    }

    /** Expenses linked to a block that has not happened yet are not counted, as in the reports. */
    private List<Expense> expenses(String email, LocalDate from, LocalDate to) {
        return expenseService.findFiltered(email, new ExpenseFilter(from, to, null, null, null, null)).stream()
                .filter(expense -> expense.getShift() == null || expense.getShift().getStatus() != ShiftStatus.SCHEDULED)
                .toList();
    }

    private LocalDate dueDate(int year, int index) {
        MonthDay due = dueDates.get(index);
        // A due date earlier in the calendar than its period's start falls in the following year, as January's does.
        return due.getMonthValue() < QUARTER_START_MONTHS[index] ? due.atYear(year + 1) : due.atYear(year);
    }

    private static BigDecimal setAside(BigDecimal net, BigDecimal percent) {
        if (percent == null) return null;
        return net.max(BigDecimal.ZERO).multiply(percent).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private static BigDecimal sum(List<TaxPayment> recorded) {
        return recorded.stream().map(TaxPayment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
    }

    private static void checkYear(int year) {
        if (year < 2000 || year > 2100) throw new InvalidFilterException("year must be between 2000 and 2100.");
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static TaxPaymentResponse response(TaxPayment payment) {
        return new TaxPaymentResponse(payment.getId(), payment.getTaxYear(), payment.getQuarter(), payment.getPaidOn(),
                payment.getAmount(), payment.getNote());
    }
}
