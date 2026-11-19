package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.dto.BlockDetailsResponse;
import com.angel.flexbuddy.dto.ExpenseResponse;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.ShiftStatus;

/**
 * Pins the website's blocks and expenses CSV downloads byte for byte. The expected files are shared with
 * import-core's export tests (and the native app's parity test), so the server and the library are held to the
 * same bytes. All data is made up.
 */
class CsvExportGoldenBytesTest {

    static final Path GOLDEN = Path.of("import-core", "src", "test", "resources",
            "com", "angel", "flexbuddy", "importcore", "export");

    private final ShiftCsvWriter shiftWriter = new ShiftCsvWriter();
    private final ExpenseCsvWriter expenseWriter = new ExpenseCsvWriter();

    @Test
    void blocksCsvMatchesTheGoldenBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        shiftWriter.write(shifts(), output);
        assertGolden("blocks.csv", output.toByteArray());
    }

    @Test
    void emptyBlocksCsvMatchesTheGoldenBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        shiftWriter.write(List.of(), output);
        assertGolden("blocks-empty.csv", output.toByteArray());
    }

    @Test
    void expensesCsvMatchesTheGoldenBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        expenseWriter.write(expenses(), output);
        assertGolden("expenses.csv", output.toByteArray());
    }

    @Test
    void emptyExpensesCsvMatchesTheGoldenBytes() throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        expenseWriter.write(List.of(), output);
        assertGolden("expenses-empty.csv", output.toByteArray());
    }

    @Test
    void blocksWriterFlushesAndLeavesTheStreamOpenWhileExpensesWriterClosesIt() throws IOException {
        TrackingStream blocks = new TrackingStream();
        shiftWriter.write(shifts(), blocks);
        assertThat(blocks.closed).isFalse();
        assertThat(blocks.flushed).isTrue();

        TrackingStream expenses = new TrackingStream();
        expenseWriter.write(expenses(), expenses);
        assertThat(expenses.closed).isTrue();
    }

    private static void assertGolden(String name, byte[] actual) throws IOException {
        Path expected = GOLDEN.resolve(name);
        if (!Files.exists(expected)) {
            // Never overwrites a committed file: the bytes land in target/ to be checked by eye first.
            Path out = Path.of("target", "golden-actual", name);
            Files.createDirectories(out.getParent());
            Files.write(out, actual);
            throw new AssertionError("Missing golden file " + expected.toAbsolutePath() + "; actual bytes in "
                    + out.toAbsolutePath());
        }
        assertThat(actual).as(name).isEqualTo(Files.readAllBytes(expected));
    }

    /** The fixtures documented in import-core's CsvExportFixtures, as the server's responses. */
    static List<ShiftResponse> shifts() {
        List<ShiftResponse> shifts = new ArrayList<>();

        // A completed block with every detail recorded and figures that round HALF_UP.
        ShiftResponse completed = shift(101L, "DLA8", LocalDate.of(2026, 9, 6), LocalTime.of(4, 0),
                LocalTime.of(7, 30), "72.00", "18.5", "90.5", 210, "25.857");
        completed.setMiles(new BigDecimal("41.30"));
        completed.setMileageCost(new BigDecimal("28.705"));
        completed.setLinkedExpenses(new BigDecimal("6.25"));
        completed.setNetPay(new BigDecimal("55.545"));
        completed.setNetHourlyRate(new BigDecimal("15.8700"));
        completed.setEarningsPerMile(new BigDecimal("2.19130"));
        completed.setCreatedAt(Instant.parse("2026-09-06T11:45:12.345Z"));
        completed.setUpdatedAt(Instant.parse("2026-09-07T02:00:00Z"));
        completed.setStatus(ShiftStatus.COMPLETED);
        completed.setDetails(new BlockDetailsResponse(LocalTime.of(3, 55, 30), LocalTime.of(7, 12), 197,
                new BigDecimal("27.565"), 18, new BigDecimal("45210.40"), new BigDecimal("45251.7"), 42, 118, 2,
                new BigDecimal("4.690"), new BigDecimal("1.7")));
        shifts.add(completed);

        // A scheduled block whose station needs quotes; it has no pay figures yet beyond its offer.
        ShiftResponse scheduled = shift(102L, "VEA7, \"North\"", LocalDate.of(2026, 9, 8), LocalTime.of(10, 30),
                LocalTime.of(14, 30), "96", null, "96", 240, null);
        scheduled.setNetPay(null);
        scheduled.setStatus(ShiftStatus.SCHEDULED);
        shifts.add(scheduled);

        // A cancelled block with a negative net, round miles and no details object at all.
        ShiftResponse cancelled = shift(103L, "Estación Peñalolén", LocalDate.of(2026, 9, 9), LocalTime.of(18, 0),
                LocalTime.of(20, 0), "30.00", "0", "30.00", 127, "14.173");
        cancelled.setMiles(new BigDecimal("100.00"));
        cancelled.setMileageCost(new BigDecimal("67.00"));
        cancelled.setLinkedExpenses(new BigDecimal("12.50"));
        cancelled.setNetPay(new BigDecimal("-49.505"));
        cancelled.setNetHourlyRate(new BigDecimal("-23.39"));
        cancelled.setEarningsPerMile(new BigDecimal("0.000"));
        cancelled.setStatus(ShiftStatus.CANCELLED);
        cancelled.setDetails(null);
        shifts.add(cancelled);

        // A late forfeit whose station looks like a formula.
        ShiftResponse lateForfeit = shift(104L, "=HYPERLINK(\"http://x.test\",\"x\")", LocalDate.of(2026, 9, 10),
                LocalTime.of(6, 0), LocalTime.of(9, 0), "0", "0", "0", 180, "0");
        lateForfeit.setStatus(ShiftStatus.FORFEITED);
        lateForfeit.setLateForfeit(true);
        shifts.add(lateForfeit);

        // A forfeit in time, its station starting with a minus sign.
        ShiftResponse forfeit = shift(105L, "-East", LocalDate.of(2026, 9, 11), LocalTime.of(6, 0),
                LocalTime.of(9, 0), "0", "0", "0", 180, "0");
        forfeit.setStatus(ShiftStatus.FORFEITED);
        forfeit.setLateForfeit(false);
        shifts.add(forfeit);

        // Every value missing: null money prints 0.00, other cells are empty, and lateForfeit is ignored.
        ShiftResponse empty = new ShiftResponse();
        empty.setLateForfeit(true);
        shifts.add(empty);

        // The other formula-leading characters the station guard covers, plus an untouched "@" in the middle.
        shifts.add(station(107L, "+1 Plus"));
        shifts.add(station(108L, "@home"));
        shifts.add(station(109L, "\tTab"));
        shifts.add(station(110L, "\rCarriage\nreturn"));
        shifts.add(station(111L, "Line\nbreak, mid@"));
        return shifts;
    }

    static List<ExpenseResponse> expenses() {
        return List.of(
                expense(201L, LocalDate.of(2026, 9, 6), ExpenseCategory.FUEL, "42.50", "Shell, exit 12", 101L,
                        "DLA8"),
                expense(202L, LocalDate.of(2026, 9, 7), ExpenseCategory.TOLL, "3.5", "He said \"hi\"", null, null),
                expense(203L, LocalDate.of(2026, 9, 8), ExpenseCategory.PARKING, "-5.00", "=SUM(A1:A9)", 102L,
                        "+VEA7"),
                expense(204L, LocalDate.of(2026, 9, 9), ExpenseCategory.MAINTENANCE, "120", "line one\r\nline two",
                        103L, "@home"),
                expense(205L, LocalDate.of(2026, 9, 10), ExpenseCategory.OTHER, "0.10",
                        "Peaje en Peñalolén — café y ñandú", 103L, "Estación Peñalolén"),
                expense(206L, null, null, null, null, null, null),
                // A formula-leading note with a line break is not prefixed: the old regex's "." stops at "\n".
                expense(207L, LocalDate.of(2026, 9, 11), ExpenseCategory.OTHER, "1E+3", "=a\nb", null, "\tTab"),
                expense(208L, LocalDate.of(2026, 9, 12), ExpenseCategory.FUEL, "7.125", "-minus note", 104L,
                        "-East"));
    }

    private static ShiftResponse shift(Long id, String station, LocalDate date, LocalTime start, LocalTime end,
            String basePay, String tips, String totalPay, int minutes, String hourlyRate) {
        return new ShiftResponse(id, station, date, start, end, money(basePay), money(tips), money(totalPay), minutes,
                money(hourlyRate), Instant.parse("2026-09-01T12:00:00Z"), Instant.parse("2026-09-01T12:30:00Z"),
                null);
    }

    private static ShiftResponse station(Long id, String station) {
        ShiftResponse shift = shift(id, station, LocalDate.of(2026, 9, 12), LocalTime.of(9, 0), LocalTime.of(13, 0),
                "100", "0", "100", 240, "25");
        shift.setStatus(ShiftStatus.COMPLETED);
        return shift;
    }

    private static ExpenseResponse expense(Long id, LocalDate date, ExpenseCategory category, String amount,
            String note, Long shiftId, String station) {
        return new ExpenseResponse(id, date, category, money(amount), note, shiftId, station,
                Instant.parse("2026-09-01T12:00:00Z"), Instant.parse("2026-09-01T12:00:00Z"), null, false);
    }

    private static BigDecimal money(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private static final class TrackingStream extends ByteArrayOutputStream {
        boolean closed;
        boolean flushed;

        @Override
        public void flush() {
            flushed = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
