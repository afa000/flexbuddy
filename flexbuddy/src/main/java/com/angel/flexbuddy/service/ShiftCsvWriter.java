package com.angel.flexbuddy.service;

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
import java.util.Arrays;

import org.springframework.stereotype.Component;

import com.angel.flexbuddy.dto.ShiftResponse;

@Component
public class ShiftCsvWriter {

    private static final String[] HEADERS = {
            "id", "date", "station", "start_time", "end_time", "minutes_worked", "hours_worked",
            "base_pay", "tips", "total_pay", "hourly_rate", "miles", "mileage_cost",
            "expenses", "net_pay", "net_hourly_rate", "earnings_per_mile", "created_at", "updated_at"
    };

    public void write(List<ShiftResponse> shifts, OutputStream output) throws IOException {
        output.write(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
        Writer writer = new OutputStreamWriter(output, StandardCharsets.UTF_8);
        writeRow(writer, Arrays.asList(HEADERS));
        for (ShiftResponse shift : shifts) {
            writeRow(writer, List.of(
                    text(shift.getId()), date(shift.getDate()), safeText(shift.getStation()),
                    time(shift.getStartTime()), time(shift.getEndTime()), text(shift.getTimeWorked()),
                    BigDecimal.valueOf(shift.getTimeWorked()).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP).toPlainString(),
                    money(shift.getBasePay()), money(shift.getTips()), money(shift.getTotalPay()),
                    money(shift.getHourlyRate()), decimal(shift.getMiles()), money(shift.getMileageCost()),
                    money(shift.getLinkedExpenses()), money(shift.getNetPay()), money(shift.getNetHourlyRate()),
                    decimal(shift.getEarningsPerMile()), instant(shift.getCreatedAt()), instant(shift.getUpdatedAt())
            ));
        }
        writer.flush();
    }

    private void writeRow(Writer writer, List<String> cells) throws IOException {
        for (int index = 0; index < cells.size(); index++) {
            if (index > 0) writer.write(',');
            writer.write(quote(cells.get(index)));
        }
        writer.write("\r\n");
    }

    private String safeText(String value) {
        if (value == null || value.isEmpty()) return "";
        char first = value.charAt(0);
        return first == '=' || first == '+' || first == '-' || first == '@' || first == '\t' || first == '\r'
                ? "'" + value : value;
    }

    private String quote(String value) {
        String safe = value == null ? "" : value;
        if (safe.contains(",") || safe.contains("\"") || safe.contains("\r") || safe.contains("\n")) {
            return "\"" + safe.replace("\"", "\"\"") + "\"";
        }
        return safe;
    }

    private String text(Object value) { return value == null ? "" : value.toString(); }
    private String decimal(BigDecimal value) { return value == null ? "" : value.stripTrailingZeros().toPlainString(); }
    private String money(BigDecimal value) { return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP).toPlainString(); }
    private String date(LocalDate value) { return value == null ? "" : value.toString(); }
    private String time(LocalTime value) { return value == null ? "" : value.format(DateTimeFormatter.ofPattern("HH:mm")); }
    private String instant(Instant value) { return value == null ? "" : value.toString(); }
}
