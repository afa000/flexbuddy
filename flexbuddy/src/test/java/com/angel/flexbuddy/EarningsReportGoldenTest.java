package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.angel.flexbuddy.dto.EarningsBucket;
import com.angel.flexbuddy.dto.EarningsReportResponse;
import com.angel.flexbuddy.dto.EarningsTotals;
import com.angel.flexbuddy.dto.ExpenseFilter;
import com.angel.flexbuddy.dto.GroupBy;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.importcore.figures.BlockStatus;
import com.angel.flexbuddy.importcore.figures.ReportBlock;
import com.angel.flexbuddy.importcore.figures.ReportCost;
import com.angel.flexbuddy.importcore.figures.ReportSelection;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.VehicleCostMethod;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.service.ExpenseService;
import com.angel.flexbuddy.service.FigureInputs;
import com.angel.flexbuddy.service.ShiftReportService;
import com.angel.flexbuddy.service.ShiftService;

/**
 * Pins {@code /shifts/reports/earnings} byte for byte: every group-by, in English and Spanish, with the IRS rate, a
 * custom rate and actual expenses, and with no filter, a padded mixed-case station filter, a status filter and a date
 * range. The expected responses are {@code earnings-report.golden} beside this class's resources, one case per line.
 * Every row is made up. Set {@code FLEXBUDDY_WRITE_GOLDEN=1} to rewrite the file, only when a change to the report is
 * intended and reviewed.
 *
 * <p>A legacy row with no date can't be stored (the column is NOT NULL) or selected (the query compares dates), so
 * the "unknown date" bucket is pinned by {@code ShiftReportServiceTest} and the library's report fixtures instead.
 */
@SpringBootTest(properties = {"flexbuddy.security.remember-me-key=golden-report-key"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EarningsReportGoldenTest {

    static final String EMAIL = "golden-report@example.com";
    static final Path GOLDEN = Path.of("src", "test", "resources", "com", "angel", "flexbuddy",
            "earnings-report.golden");
    /** The library's expected figures for the same rows (EarningsReportFixtures in import-core). */
    static final Path LIBRARY_EXPECTED = Path.of("import-core", "src", "test", "resources", "com", "angel",
            "flexbuddy", "importcore", "figures", "earnings-report.txt");

    @Autowired MockMvc mockMvc;
    @Autowired AppUserRepository users;
    @Autowired ShiftRepository shifts;
    @Autowired ExpenseRepository expenses;
    @Autowired JdbcTemplate jdbc;
    @Autowired ShiftReportService reportService;
    @Autowired ShiftService shiftService;
    @Autowired ExpenseService expenseService;

    AppUser owner;

    @BeforeEach
    void seed() {
        wipe();
        owner = users.save(new AppUser("Golden Report", EMAIL, "{noop}not-a-real-password-1"));
        seedRows(owner, shifts, expenses);
    }

    /** Native deletes, because the JPA repository can't see soft-deleted rows and would leave them behind. */
    @AfterEach
    void wipe() {
        jdbc.update("delete from expense where owner_id in (select id from app_users where email = ?)", EMAIL);
        jdbc.update("delete from shift where owner_id in (select id from app_users where email = ?)", EMAIL);
        users.findByEmailIgnoreCase(EMAIL).ifPresent(users::delete);
    }

    @Test
    void everyReportMatchesTheGoldenResponses() throws Exception {
        Map<String, String> actual = new LinkedHashMap<>();
        for (Settings settings : Settings.values()) {
            settings.apply(owner);
            users.save(owner);
            for (String groupBy : List.of("week", "month", "year", "station")) {
                for (Filter filter : Filter.values()) {
                    if (settings != Settings.IRS && filter != Filter.NONE) continue;
                    for (String language : List.of("en", "es")) {
                        if (language.equals("es") && (settings != Settings.IRS || filter != Filter.NONE)) continue;
                        String name = settings.name().toLowerCase() + "/" + groupBy + "/" + filter.name().toLowerCase()
                                + "/" + language;
                        MockHttpServletRequestBuilder request = get("/shifts/reports/earnings").with(user(EMAIL))
                                .header("Accept-Language", language).param("groupBy", groupBy);
                        filter.params.forEach(request::param);
                        String body = mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse()
                                .getContentAsString(StandardCharsets.UTF_8);
                        actual.put(name, body);
                    }
                }
            }
        }
        StringBuilder text = new StringBuilder();
        actual.forEach((name, body) -> text.append(name).append(' ').append(body).append('\n'));
        if ("1".equals(System.getenv("FLEXBUDDY_WRITE_GOLDEN"))) {
            Files.writeString(GOLDEN, text, StandardCharsets.UTF_8);
        }
        assertThat(text.toString()).isEqualTo(golden());
    }

    /**
     * The library's report fixtures are these rows as plain inputs, and its expected figures are the website's: for
     * each of its cases, the website's buckets (key, period and every figure, in order) and totals are the same.
     */
    @Test
    void theWebsitesFiguresAreTheLibrarysFixtures() throws Exception {
        String expected = Files.readString(LIBRARY_EXPECTED, StandardCharsets.UTF_8).replace("\r\n", "\n");
        StringBuilder text = new StringBuilder();
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        try {
            for (Settings settings : Settings.values()) {
                settings.apply(owner);
                users.save(owner);
                for (GroupBy groupBy : List.of(GroupBy.WEEK, GroupBy.MONTH, GroupBy.YEAR, GroupBy.STATION)) {
                    for (Filter filter : Filter.values()) {
                        if (settings != Settings.IRS && filter != Filter.NONE) continue;
                        EarningsReportResponse report = reportService.earnings(EMAIL, filter.shiftFilter(), groupBy);
                        text.append(settings.name().toLowerCase()).append('/').append(groupBy.name().toLowerCase())
                                .append('/').append(filter.name().toLowerCase()).append(' ').append(render(report))
                                .append('\n');
                    }
                }
            }
        } finally {
            LocaleContextHolder.resetLocaleContext();
        }
        assertThat(text.toString()).isEqualTo(expected);
    }

    /**
     * The library's selection helpers, applied to every live row of the account, pick exactly the rows the website's
     * queries pick, for station, status and date filters and their combinations.
     */
    @Test
    void theLibrarysSelectionPicksTheSameRowsAsTheWebsitesQueries() {
        List<Shift> allBlocks = shiftService.findFiltered(EMAIL, ShiftFilter.report(null, null, null, null, "all"));
        List<Expense> allExpenses = expenseService.findFiltered(EMAIL,
                new ExpenseFilter(null, null, null, null, null, null));
        Map<Long, ExpenseService.LinkedShift> links = expenseService.linkedShifts(EMAIL, allExpenses);
        List<ReportBlock> blocks = FigureInputs.reportBlocks(allBlocks);
        List<ReportCost> costs = FigureInputs.reportCosts(allExpenses, links);
        assertThat(allBlocks).hasSize(8);
        assertThat(allExpenses).hasSize(8);

        int checked = 0;
        for (String station : Arrays.asList(null, "", "  vEa7 ", " vea7 ", "DLA8", "dcy1", "BDL4", "nowhere")) {
            for (String status : Arrays.asList(null, "COMPLETED", "CANCELLED,FORFEITED", "SCHEDULED", "all")) {
                for (LocalDate[] range : List.of(new LocalDate[] {null, null},
                        new LocalDate[] {LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)},
                        new LocalDate[] {LocalDate.of(2026, 12, 31), null},
                        new LocalDate[] {null, LocalDate.of(2026, 1, 2)})) {
                    ShiftFilter filter = ShiftFilter.report(range[0], range[1], station, null, status);
                    List<Long> sqlBlocks = ids(shiftService.findFiltered(EMAIL, filter));
                    List<Long> sqlExpenses = expenseIds(expenseService.countable(EMAIL, expenseService.findFiltered(
                            EMAIL, new ExpenseFilter(range[0], range[1], filter.station(), null, null, null))));
                    Set<BlockStatus> statuses = status == null ? null : filter.statuses().stream()
                            .map(FigureInputs::status).collect(Collectors.toSet());
                    List<Long> libraryBlocks = new ArrayList<>();
                    for (int index = 0; index < blocks.size(); index++) {
                        if (ReportSelection.blockMatches(blocks.get(index), range[0], range[1], station, statuses)) {
                            libraryBlocks.add(allBlocks.get(index).getId());
                        }
                    }
                    List<Long> libraryExpenses = new ArrayList<>();
                    for (int index = 0; index < costs.size(); index++) {
                        if (ReportSelection.costMatches(costs.get(index), range[0], range[1], station)) {
                            libraryExpenses.add(allExpenses.get(index).getId());
                        }
                    }
                    String name = station + "/" + status + "/" + range[0] + ".." + range[1];
                    assertThat(libraryBlocks).as("blocks " + name).isEqualTo(sqlBlocks);
                    assertThat(libraryExpenses).as("expenses " + name).isEqualTo(sqlExpenses);
                    checked++;
                }
            }
        }
        assertThat(checked).isEqualTo(160);
    }

    /** A report in the library fixtures' line format (see EarningsReportFixtures.render). */
    private static String render(EarningsReportResponse report) {
        StringBuilder line = new StringBuilder();
        for (EarningsBucket b : report.buckets()) {
            line.append(join(b.key(), b.periodStart(), b.periodEnd(), b.shifts(), b.basePay(), b.tips(),
                    b.totalEarnings(), b.minutesWorked(), b.hourlyRate(), b.miles(), b.mileageCost(), b.expenses(),
                    b.deductions(), b.netEarnings(), b.netHourlyRate(), b.timedShifts(), b.clockedMinutes(),
                    b.clockedHourlyRate(), b.averageFinishedEarlyMinutes(), b.shiftsWithRouteData(), b.averageStops(),
                    b.averageMinutesPerStop(), b.returnsRate(), b.netMissingMiles())).append(" | ");
        }
        EarningsTotals t = report.totals();
        line.append("totals ").append(join(t.shifts(), t.basePay(), t.tips(), t.totalEarnings(), t.minutesWorked(),
                t.hourlyRate(), t.miles(), t.mileageCost(), t.expenses(), t.deductions(), t.netEarnings(),
                t.netHourlyRate()));
        return line.toString();
    }

    private static String join(Object... values) {
        StringBuilder joined = new StringBuilder();
        for (Object value : values) {
            if (joined.length() > 0) joined.append(',');
            joined.append(value == null ? "-" : value instanceof BigDecimal amount ? amount.toPlainString() : value);
        }
        return joined.toString();
    }

    private static List<Long> ids(List<Shift> rows) {
        return rows.stream().map(Shift::getId).toList();
    }

    private static List<Long> expenseIds(List<Expense> rows) {
        return rows.stream().map(Expense::getId).toList();
    }

    static String golden() throws IOException {
        return Files.readString(GOLDEN, StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    enum Settings {
        IRS(VehicleCostMethod.STANDARD_MILEAGE, null),
        CUSTOM(VehicleCostMethod.STANDARD_MILEAGE, new BigDecimal("0.655")),
        ACTUAL(VehicleCostMethod.ACTUAL_EXPENSES, null);

        final VehicleCostMethod method;
        final BigDecimal rate;

        Settings(VehicleCostMethod method, BigDecimal rate) {
            this.method = method;
            this.rate = rate;
        }

        void apply(AppUser user) {
            user.setVehicleCostMethod(method);
            user.setMileageRate(rate);
        }
    }

    enum Filter {
        NONE(Map.of()),
        STATION(Map.of("station", "  vEa7 ")),
        STATUS(Map.of("status", "COMPLETED,FORFEITED")),
        RANGE(Map.of("from", "2026-01-01", "to", "2026-12-31"));

        final Map<String, String> params;

        Filter(Map<String, String> params) {
            this.params = params;
        }

        ShiftFilter shiftFilter() {
            return ShiftFilter.report(params.get("from") == null ? null : LocalDate.parse(params.get("from")),
                    params.get("to") == null ? null : LocalDate.parse(params.get("to")), params.get("station"), null,
                    params.get("status"));
        }
    }

    /**
     * One made-up account: completed, cancelled, forfeited, scheduled and deleted blocks across ISO week 1 and week 53
     * and the IRS rate changes; padded and mixed-case stations; with and without miles, actual times, stops, packages
     * and returns; and expenses unlinked, linked, linked to a scheduled block, linked to a deleted block and deleted.
     */
    static void seedRows(AppUser owner, ShiftRepository shifts, ExpenseRepository expenses) {
        // 2025-12-30 is in ISO week 2026-W01 but calendar year 2025.
        Shift first = shifts.save(block(owner, "VEA7", "2025-12-30", "08:00", "12:00", "80.00", "12.50", "31.4",
                ShiftStatus.COMPLETED, "08:05", "11:40", 42, 110, 3));
        Shift padded = shifts.save(block(owner, " vea7 ", "2026-01-02", "10:00", "13:30", "63.00", "0.00", null,
                ShiftStatus.COMPLETED, null, null, 30, null, 1));
        shifts.save(block(owner, "DLA8", "2026-06-30", "09:15", "12:45", "61.25", "4.75", "18.6",
                ShiftStatus.COMPLETED, "09:20", "12:10", 33, 70, 0));
        shifts.save(block(owner, "DLA8", "2026-12-28", "18:00", "21:00", "54.00", "5.00", "12.0",
                ShiftStatus.CANCELLED, null, null, null, null, null));
        Shift forfeited = shifts.save(block(owner, "dla8", "2026-12-31", "06:00", "09:00", "54.00", "0.00", "8.0",
                ShiftStatus.FORFEITED, null, null, null, null, null));
        Shift scheduled = shifts.save(block(owner, "VEA7", "2027-01-03", "07:00", "11:00", "90.00", "0.00", null,
                ShiftStatus.SCHEDULED, null, null, null, null, null));
        // Overnight, and its packages are zero, so returns fall back to nothing rather than to stops.
        Shift overnight = shifts.save(block(owner, "DLA8", "2027-01-04", "22:00", "02:00", "96.00", "20.25", "44.7",
                ShiftStatus.COMPLETED, "22:10", "01:35", 55, 0, 2));
        // Ran over, with zero stops recorded.
        Shift over = shifts.save(block(owner, "BDL4", "2027-02-15", "09:00", "13:00", "72.33", "9.99", "20.3",
                ShiftStatus.COMPLETED, "09:00", "13:20", 0, null, null));
        Shift deleted = block(owner, "DCY1", "2026-12-29", "10:00", "14:00", "70.00", "0.00", "10.0",
                ShiftStatus.COMPLETED, null, null, null, null, null);
        deleted.setDeletedAt(Instant.parse("2027-01-05T12:00:00Z"));
        deleted = shifts.save(deleted);

        expenses.save(expense(owner, "2026-01-02", ExpenseCategory.FUEL, "25.40", padded, null));
        expenses.save(expense(owner, "2026-12-31", ExpenseCategory.TOLL, "6.75", null, null));
        expenses.save(expense(owner, "2027-01-03", ExpenseCategory.PARKING, "4.00", scheduled, null));
        expenses.save(expense(owner, "2026-12-29", ExpenseCategory.MAINTENANCE, "120.00", deleted, null));
        expenses.save(expense(owner, "2027-01-04", ExpenseCategory.OTHER, "3.33", overnight, null));
        expenses.save(expense(owner, "2027-02-15", ExpenseCategory.FUEL, "41.10", over, null));
        expenses.save(expense(owner, "2026-12-31", ExpenseCategory.TOLL, "2.50", forfeited, null));
        expenses.save(expense(owner, "2025-12-30", ExpenseCategory.FUEL, "18.05", first, null));
        expenses.save(expense(owner, "2026-07-01", ExpenseCategory.OTHER, "9.99", null,
                Instant.parse("2026-07-02T12:00:00Z")));
    }

    static Shift block(AppUser owner, String station, String date, String start, String end, String base, String tips,
            String miles, ShiftStatus status, String actualStart, String actualEnd, Integer stops, Integer packages,
            Integer returns) {
        Shift shift = new Shift(null, station, LocalDate.parse(date), LocalTime.parse(start), LocalTime.parse(end),
                new BigDecimal(base), new BigDecimal(tips), owner);
        shift.setMiles(miles == null ? null : new BigDecimal(miles));
        shift.setStatus(status);
        shift.setActualStart(actualStart == null ? null : LocalTime.parse(actualStart));
        shift.setActualEnd(actualEnd == null ? null : LocalTime.parse(actualEnd));
        shift.setStops(stops);
        shift.setPackages(packages);
        shift.setReturns(returns);
        return shift;
    }

    static Expense expense(AppUser owner, String date, ExpenseCategory category, String amount, Shift shift,
            Instant deletedAt) {
        Expense expense = new Expense();
        expense.setOwner(owner);
        expense.setDate(LocalDate.parse(date));
        expense.setCategory(category);
        expense.setAmount(new BigDecimal(amount));
        expense.setShift(shift);
        expense.setDeletedAt(deletedAt);
        return expense;
    }
}
