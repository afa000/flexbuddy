package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.dto.ShiftResponse;

class ShiftCsvWriterTest {

    private final ShiftCsvWriter writer = new ShiftCsvWriter();

    @Test
    void writesExcelFriendlyCsvWithBomCrLfQuotingAndFixedMoney() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ShiftResponse shift = new ShiftResponse(
                7L, "VEA7, \"North\"", LocalDate.of(2026, 9, 6), LocalTime.of(4, 0),
                LocalTime.of(7, 30), new BigDecimal("124.5"), BigDecimal.ZERO,
                new BigDecimal("124.5"), 210, new BigDecimal("35.571"),
                Instant.parse("2026-09-01T12:00:00Z"), Instant.parse("2026-09-02T12:00:00Z"), null);

        writer.write(List.of(shift), output);

        byte[] bytes = output.toByteArray();
        assertThat(bytes).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
        String csv = new String(bytes, 3, bytes.length - 3, StandardCharsets.UTF_8);
        assertThat(csv).startsWith("id,date,station,start_time,end_time,minutes_worked,hours_worked,");
        assertThat(csv).contains("\"VEA7, \"\"North\"\"\"");
        assertThat(csv).contains(",04:00,07:30,210,3.50,124.50,0.00,124.50,35.57,");
        assertThat(csv.replace("\r\n", "")).doesNotContain("\n", "\r");
        assertThat(csv.split("\r\n", -1)).hasSize(3);
    }

    @Test
    void protectsStationCellsFromSpreadsheetFormulaExecution() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.write(List.of(response("=HYPERLINK(\"bad\")")), output);

        String csv = new String(output.toByteArray(), StandardCharsets.UTF_8);
        assertThat(csv).contains("'=" + "HYPERLINK(\"\"bad\"\")");
    }

    @Test
    void emptyExportContainsOnlyTheHeader() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writer.write(List.of(), output);

        String csv = new String(output.toByteArray(), 3, output.size() - 3, StandardCharsets.UTF_8);
        assertThat(csv.split("\r\n", -1)).hasSize(2);
    }

    private ShiftResponse response(String station) {
        return new ShiftResponse(1L, station, LocalDate.of(2026, 9, 6), LocalTime.of(9, 0),
                LocalTime.of(13, 0), new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"),
                240, new BigDecimal("25"), Instant.parse("2026-09-01T12:00:00Z"),
                Instant.parse("2026-09-01T12:00:00Z"), null);
    }
}
