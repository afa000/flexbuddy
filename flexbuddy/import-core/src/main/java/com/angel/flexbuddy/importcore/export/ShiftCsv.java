package com.angel.flexbuddy.importcore.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/**
 * Writes the blocks CSV that the website downloads, byte for byte. It only formats: the consumer selects the
 * signed-in account's rows and computes their figures first.
 *
 * <ul>
 *   <li>UTF-8 <strong>with</strong> a byte order mark (EF BB BF), so spreadsheet apps read accents correctly; the
 *       expenses CSV ({@link ExpenseCsv}) has none.</li>
 *   <li>CRLF after every row, the header included. A cell is quoted only when it contains a comma, a double quote,
 *       CR or LF, with quotes doubled.</li>
 *   <li>Money is two decimals, HALF_UP, and a missing amount prints {@code 0.00}, except {@code actual_hourly_rate},
 *       which prints empty. Other decimals strip trailing zeros. Times are {@code HH:mm}, instants ISO-8601, and
 *       {@code hours_worked} is minutes / 60 at two decimals, HALF_UP.</li>
 *   <li>Only the station is guarded against spreadsheet formulas: one starting with {@code =}, {@code +}, {@code -},
 *       {@code @}, a tab or CR gets a {@code '} prefix.</li>
 *   <li>{@code late_forfeit} prints only when the status name is {@code FORFEITED}.</li>
 * </ul>
 *
 * <p>The writer is flushed and the stream is left <strong>open</strong>; the caller closes it. (The expenses writer
 * closes its stream.)
 */
public final class ShiftCsv {

    private static final String[] HEADERS = {
            "id", "date", "station", "start_time", "end_time", "minutes_worked", "hours_worked",
            "base_pay", "tips", "total_pay", "hourly_rate", "miles", "mileage_cost",
            "expenses", "net_pay", "net_hourly_rate", "earnings_per_mile", "created_at", "updated_at",
            "status", "actual_start", "actual_end", "actual_minutes", "actual_hourly_rate",
            "odometer_start", "odometer_end", "stops", "packages", "returns", "minutes_per_stop", "late_forfeit"
    };
    private static final String FORFEITED = "FORFEITED";
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT);

    private ShiftCsv() {
    }

    public static void write(List<ShiftCsvRow> rows, OutputStream output) throws IOException {
        output.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writeRow(writer, HEADERS);
        for (ShiftCsvRow row : rows) {
            writeRow(writer, new String[] {
                    text(row.id()), date(row.date()), safeText(row.station()),
                    time(row.startTime()), time(row.endTime()), text(row.minutesWorked()),
                    BigDecimal.valueOf(row.minutesWorked()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP)
                            .toPlainString(),
                    money(row.basePay()), money(row.tips()), money(row.totalPay()),
                    money(row.hourlyRate()), decimal(row.miles()), money(row.mileageCost()),
                    money(row.linkedExpenses()), money(row.netPay()), money(row.netHourlyRate()),
                    decimal(row.earningsPerMile()), instant(row.createdAt()), instant(row.updatedAt()),
                    row.status() == null ? "" : row.status(),
                    time(row.actualStart()), time(row.actualEnd()),
                    text(row.actualMinutes()), optionalMoney(row.actualHourlyRate()),
                    decimal(row.odometerStart()), decimal(row.odometerEnd()),
                    text(row.stops()), text(row.packages()), text(row.returns()),
                    decimal(row.minutesPerStop()),
                    FORFEITED.equals(row.status()) ? String.valueOf(row.lateForfeit()) : ""
            });
        }
        writer.flush();
    }

    private static void writeRow(Writer writer, String[] cells) throws IOException {
        for (int index = 0; index < cells.length; index++) {
            if (index > 0) writer.write(',');
            writer.write(quote(cells[index]));
        }
        writer.write("\r\n");
    }

    private static String safeText(String value) {
        if (value == null || value.isEmpty()) return "";
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + value : value;
    }

    private static String quote(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\r") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private static String optionalMoney(BigDecimal value) {
        return value == null ? "" : money(value);
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private static String money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private static String date(LocalDate value) {
        return value == null ? "" : value.toString();
    }

    private static String time(LocalTime value) {
        return value == null ? "" : value.format(TIME);
    }

    private static String instant(Instant value) {
        return value == null ? "" : value.toString();
    }
}
