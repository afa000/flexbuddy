package com.angel.flexbuddy.importcore.export;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Made-up rows whose CSVs are the golden files beside this class ({@code blocks.csv}, {@code blocks-empty.csv},
 * {@code expenses.csv}, {@code expenses-empty.csv}). The website's CsvExportGoldenBytesTest builds the same rows
 * from its responses and expects the same bytes, and the native app's export parity test can reuse both.
 */
public final class CsvExportFixtures {

    private static final Instant CREATED = Instant.parse("2026-09-01T12:00:00Z");
    private static final Instant UPDATED = Instant.parse("2026-09-01T12:30:00Z");

    private CsvExportFixtures() {
    }

    public static List<ShiftCsvRow> shifts() {
        List<ShiftCsvRow> rows = new ArrayList<>();
        // A completed block with every detail recorded and figures that round HALF_UP.
        rows.add(new ShiftCsvRow(101L, LocalDate.of(2026, 9, 6), "DLA8", LocalTime.of(4, 0), LocalTime.of(7, 30), 210,
                money("72.00"), money("18.5"), money("90.5"), money("25.857"), money("41.30"), money("28.705"),
                money("6.25"), money("55.545"), money("15.8700"), money("2.19130"),
                Instant.parse("2026-09-06T11:45:12.345Z"), Instant.parse("2026-09-07T02:00:00Z"), "COMPLETED",
                LocalTime.of(3, 55, 30), LocalTime.of(7, 12), 197, money("27.565"), money("45210.40"),
                money("45251.7"), 42, 118, 2, money("4.690"), false));
        // A scheduled block whose station needs quotes; tips and net are missing.
        rows.add(new ShiftCsvRow(102L, LocalDate.of(2026, 9, 8), "VEA7, \"North\"", LocalTime.of(10, 30),
                LocalTime.of(14, 30), 240, money("96"), null, money("96"), null, null, money("0.00"), money("0.00"),
                null, null, null, CREATED, UPDATED, "SCHEDULED", null, null, null, null, null, null, null, null, null,
                null, false));
        // A cancelled block with a negative net, round miles and no details.
        rows.add(new ShiftCsvRow(103L, LocalDate.of(2026, 9, 9), "Estación Peñalolén", LocalTime.of(18, 0),
                LocalTime.of(20, 0), 127, money("30.00"), money("0"), money("30.00"), money("14.173"),
                money("100.00"), money("67.00"), money("12.50"), money("-49.505"), money("-23.39"), money("0.000"),
                CREATED, UPDATED, "CANCELLED", null, null, null, null, null, null, null, null, null, null, false));
        // A late forfeit whose station looks like a formula.
        rows.add(forfeit(104L, "=HYPERLINK(\"http://x.test\",\"x\")", LocalDate.of(2026, 9, 10), true));
        // A forfeit in time, its station starting with a minus sign.
        rows.add(forfeit(105L, "-East", LocalDate.of(2026, 9, 11), false));
        // Every value missing: null money prints 0.00, other cells are empty, and lateForfeit is ignored.
        rows.add(new ShiftCsvRow(null, null, null, null, null, 0, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, null, null, null, true));
        // The other formula-leading characters the station guard covers, and line breaks that need quotes.
        rows.add(station(107L, "+1 Plus"));
        rows.add(station(108L, "@home"));
        rows.add(station(109L, "\tTab"));
        rows.add(station(110L, "\rCarriage\nreturn"));
        rows.add(station(111L, "Line\nbreak, mid@"));
        return rows;
    }

    public static List<ExpenseCsvRow> expenses() {
        List<ExpenseCsvRow> rows = new ArrayList<>();
        rows.add(new ExpenseCsvRow(LocalDate.of(2026, 9, 6), "FUEL", money("42.50"), "Shell, exit 12", 101L, "DLA8"));
        rows.add(new ExpenseCsvRow(LocalDate.of(2026, 9, 7), "TOLL", money("3.5"), "He said \"hi\"", null, null));
        rows.add(new ExpenseCsvRow(LocalDate.of(2026, 9, 8), "PARKING", money("-5.00"), "=SUM(A1:A9)", 102L,
                "+VEA7"));
        rows.add(new ExpenseCsvRow(LocalDate.of(2026, 9, 9), "MAINTENANCE", money("120"), "line one\r\nline two",
                103L, "@home"));
        rows.add(new ExpenseCsvRow(LocalDate.of(2026, 9, 10), "OTHER", money("0.10"),
                "Peaje en Peñalolén — café y ñandú", 103L, "Estación Peñalolén"));
        rows.add(new ExpenseCsvRow(null, null, null, null, null, null));
        // A formula-leading note with a line break is not prefixed: "." in the guard stops at "\n".
        rows.add(new ExpenseCsvRow(LocalDate.of(2026, 9, 11), "OTHER", money("1E+3"), "=a\nb", null, "\tTab"));
        rows.add(new ExpenseCsvRow(LocalDate.of(2026, 9, 12), "FUEL", money("7.125"), "-minus note", 104L, "-East"));
        return rows;
    }

    /** The golden file's bytes, read from the test classpath. */
    public static byte[] golden(String name) throws IOException {
        try (InputStream input = CsvExportFixtures.class.getResourceAsStream(name)) {
            if (input == null) throw new IOException("Missing golden file " + name);
            return input.readAllBytes();
        }
    }

    private static ShiftCsvRow forfeit(Long id, String station, LocalDate date, boolean late) {
        return new ShiftCsvRow(id, date, station, LocalTime.of(6, 0), LocalTime.of(9, 0), 180, money("0"),
                money("0"), money("0"), money("0"), null, money("0.00"), money("0.00"), money("0"), money("0"),
                null, CREATED, UPDATED, "FORFEITED", null, null, null, null, null, null, null, null, null, null,
                late);
    }

    private static ShiftCsvRow station(Long id, String station) {
        return new ShiftCsvRow(id, LocalDate.of(2026, 9, 12), station, LocalTime.of(9, 0), LocalTime.of(13, 0), 240,
                money("100"), money("0"), money("100"), money("25"), null, money("0.00"), money("0.00"),
                money("100"), money("25"), null, CREATED, UPDATED, "COMPLETED", null, null, null, null, null, null,
                null, null, null, null, false);
    }

    private static BigDecimal money(String value) {
        return value == null ? null : new BigDecimal(value);
    }
}
