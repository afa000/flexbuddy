package com.angel.flexbuddy.service;

import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.angel.flexbuddy.dto.BlockDetailsResponse;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.importcore.export.ShiftCsv;
import com.angel.flexbuddy.importcore.export.ShiftCsvRow;

/** The website's blocks CSV: maps the signed-in driver's responses to rows for the shared {@link ShiftCsv}. */
@Component
public class ShiftCsvWriter {

    /** Writes the blocks CSV; flushes and leaves the stream open (see {@link ShiftCsv}). */
    public void write(List<ShiftResponse> shifts, OutputStream output) throws IOException {
        List<ShiftCsvRow> rows = new ArrayList<>(shifts.size());
        for (ShiftResponse shift : shifts) rows.add(row(shift));
        ShiftCsv.write(rows, output);
    }

    static ShiftCsvRow row(ShiftResponse shift) {
        BlockDetailsResponse details = shift.getDetails() == null ? BlockDetailsResponse.NONE : shift.getDetails();
        return new ShiftCsvRow(shift.getId(), shift.getDate(), shift.getStation(), shift.getStartTime(),
                shift.getEndTime(), shift.getTimeWorked(), shift.getBasePay(), shift.getTips(), shift.getTotalPay(),
                shift.getHourlyRate(), shift.getMiles(), shift.getMileageCost(), shift.getLinkedExpenses(),
                shift.getNetPay(), shift.getNetHourlyRate(), shift.getEarningsPerMile(), shift.getCreatedAt(),
                shift.getUpdatedAt(), shift.getStatus() == null ? null : shift.getStatus().name(),
                details.actualStart(), details.actualEnd(), details.actualMinutes(), details.actualHourlyRate(),
                details.odometerStart(), details.odometerEnd(), details.stops(), details.packages(),
                details.returns(), details.minutesPerStop(), shift.isLateForfeit());
    }
}
