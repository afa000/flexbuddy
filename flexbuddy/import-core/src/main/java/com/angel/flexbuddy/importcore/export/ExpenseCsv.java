package com.angel.flexbuddy.importcore.export;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes the expenses CSV that the website downloads, byte for byte. It only formats: the consumer selects the
 * signed-in account's rows first.
 *
 * <ul>
 *   <li>UTF-8 <strong>without</strong> a byte order mark; the blocks CSV ({@link ShiftCsv}) has one.</li>
 *   <li>Header {@code Date,Category,Amount,Note,Shift ID,Station}, CRLF after every row, every cell quoted with
 *       quotes doubled, a missing value as an empty cell.</li>
 *   <li>The date is ISO, the category its name and the amount {@link java.math.BigDecimal#toString()}.</li>
 *   <li>Any cell matching {@code ^[=+@-].*} gets a {@code '} prefix, so a negative amount does too. As in the
 *       website's writer, {@code .} does not match a line break, so a cell whose first line break follows the
 *       leading character is not prefixed. The guard differs from the blocks CSV's on purpose.</li>
 * </ul>
 *
 * <p>The stream is <strong>closed</strong> when writing ends. (The blocks writer only flushes.)
 */
public final class ExpenseCsv {

    private static final String HEADER = "Date,Category,Amount,Note,Shift ID,Station\r\n";

    private ExpenseCsv() {
    }

    public static void write(List<ExpenseCsvRow> rows, OutputStream output) throws IOException {
        try (var writer = new OutputStreamWriter(output, StandardCharsets.UTF_8)) {
            writer.write(HEADER);
            for (ExpenseCsvRow row : rows) {
                writer.write(String.join(",", csv(row.date()), csv(row.category()), csv(row.amount()),
                        csv(row.note()), csv(row.blockId()), csv(row.station())) + "\r\n");
            }
        }
    }

    private static String csv(Object value) {
        String text = value == null ? "" : String.valueOf(value);
        if (text.matches("^[=+@-].*")) text = "'" + text;
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
