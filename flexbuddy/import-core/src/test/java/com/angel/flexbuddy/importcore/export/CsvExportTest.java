package com.angel.flexbuddy.importcore.export;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;

/** The shared writers produce the website's golden bytes from plain rows. */
class CsvExportTest {

    @Test
    void blocksCsvMatchesTheGoldenBytes() throws IOException {
        assertArrayEquals(CsvExportFixtures.golden("blocks.csv"), shifts(CsvExportFixtures.shifts()));
    }

    @Test
    void emptyBlocksCsvIsTheBomAndHeader() throws IOException {
        assertArrayEquals(CsvExportFixtures.golden("blocks-empty.csv"), shifts(List.of()));
    }

    @Test
    void expensesCsvMatchesTheGoldenBytes() throws IOException {
        assertArrayEquals(CsvExportFixtures.golden("expenses.csv"), expenses(CsvExportFixtures.expenses()));
    }

    @Test
    void emptyExpensesCsvIsTheHeader() throws IOException {
        assertArrayEquals(CsvExportFixtures.golden("expenses-empty.csv"), expenses(List.of()));
    }

    @Test
    void onlyTheBlocksCsvStartsWithAByteOrderMark() throws IOException {
        byte[] blocks = shifts(List.of());
        byte[] expenses = expenses(List.of());
        assertArrayEquals(new byte[] {(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, java.util.Arrays.copyOf(blocks, 3));
        assertTrue(expenses[0] == 'D');
    }

    @Test
    void blocksWriterFlushesAndLeavesTheStreamOpenWhileExpensesWriterClosesIt() throws IOException {
        TrackingStream blocks = new TrackingStream();
        ShiftCsv.write(CsvExportFixtures.shifts(), blocks);
        assertFalse(blocks.closed);
        assertTrue(blocks.flushed);

        TrackingStream expenses = new TrackingStream();
        ExpenseCsv.write(CsvExportFixtures.expenses(), expenses);
        assertTrue(expenses.closed);
    }

    private static byte[] shifts(List<ShiftCsvRow> rows) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ShiftCsv.write(rows, output);
        return output.toByteArray();
    }

    private static byte[] expenses(List<ExpenseCsvRow> rows) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ExpenseCsv.write(rows, output);
        return output.toByteArray();
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
