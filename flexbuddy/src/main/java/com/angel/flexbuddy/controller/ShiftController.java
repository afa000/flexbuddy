package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.time.Clock;
import java.time.format.DateTimeFormatter;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.angel.flexbuddy.dto.BlockEvaluationRequest;
import com.angel.flexbuddy.dto.BlockEvaluationResponse;
import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.EarningsReportResponse;
import com.angel.flexbuddy.dto.GroupBy;
import com.angel.flexbuddy.dto.OdometerReadingResponse;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.ShiftImportPreviewResponse;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.ShiftStatisticsResponse;
import com.angel.flexbuddy.dto.ShiftStatusRequest;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.service.BlockEvaluator;
import com.angel.flexbuddy.service.ShiftImportService;
import com.angel.flexbuddy.service.ShiftReportService;
import com.angel.flexbuddy.service.ShiftService;
import com.angel.flexbuddy.service.ShiftCsvWriter;
import com.angel.flexbuddy.service.ExpenseService;
import com.angel.flexbuddy.dto.ExpenseResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/shifts")
public class ShiftController {

    private final ShiftService shiftService;
    private final ShiftReportService reportService;
    private final ShiftImportService shiftImportService;
    private final ShiftCsvWriter csvWriter;
    private final Clock clock;
    private final ExpenseService expenseService;
    private final BlockEvaluator blockEvaluator;

    public ShiftController(ShiftService shiftService, ShiftReportService reportService,
            ShiftImportService shiftImportService, ShiftCsvWriter csvWriter, Clock clock,
            ExpenseService expenseService, BlockEvaluator blockEvaluator) {
        this.shiftService = shiftService;
        this.reportService = reportService;
        this.shiftImportService = shiftImportService;
        this.csvWriter = csvWriter;
        this.clock = clock;
        this.expenseService = expenseService;
        this.blockEvaluator = blockEvaluator;
    }

    @GetMapping
    public List<ShiftResponse> getShifts(Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String station,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String sort,
            @RequestParam(name = "dir", required = false) String direction,
            @RequestParam(required = false) String status) {
        return shiftService.getShifts(principal.getName(),
                ShiftFilter.of(from, to, station, query, sort, direction, status));
    }

    @GetMapping("/statistics")
    public ShiftStatisticsResponse getStatistics(Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String station,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String status) {
        return reportService.statistics(principal.getName(), ShiftFilter.report(from, to, station, query, status));
    }

    @GetMapping("/stations")
    public List<String> getStations(Principal principal) {
        return shiftService.getStations(principal.getName());
    }

    @GetMapping("/{id}/expenses")
    public List<ExpenseResponse> getLinkedExpenses(Principal principal, @PathVariable Long id) {
        return expenseService.getForShift(principal.getName(), id);
    }

    @GetMapping("/reports/heatmap")
    public com.angel.flexbuddy.dto.HeatmapResponse getHeatmap(Principal principal,
            @RequestParam(required = false) String metric,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String station,
            @RequestParam(name = "q", required = false) String query) {
        return reportService.heatmap(principal.getName(), ShiftFilter.report(from, to, station, query),
                com.angel.flexbuddy.dto.HeatmapMetric.parse(metric));
    }

    @GetMapping("/reports/earnings")
    public EarningsReportResponse getEarningsReport(Principal principal,
            @RequestParam String groupBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String station,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String status) {
        return reportService.earnings(principal.getName(), ShiftFilter.report(from, to, station, query, status),
                GroupBy.parse(groupBy));
    }

    @PostMapping
    public ShiftResponse createShift(Principal principal, @Valid @RequestBody CreateShiftRequest request,
            @RequestHeader(value = RequestIds.HEADER, required = false) String key) {
        return shiftService.createShift(principal.getName(), request, RequestIds.parse(key));
    }

    @PostMapping("/evaluate")
    public BlockEvaluationResponse evaluate(Principal principal, @Valid @RequestBody BlockEvaluationRequest request) {
        return blockEvaluator.evaluate(principal.getName(), request);
    }

    @PostMapping(value = "/import-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ShiftImportPreviewResponse importPreview(Principal principal,
            @RequestParam("screenshot") MultipartFile screenshot) {
        return shiftImportService.createPreview(principal.getName(), screenshot);
    }

    @PutMapping("/{id}")
    public ShiftResponse updateShift(Principal principal, @PathVariable Long id,
            @Valid @RequestBody UpdateShiftRequest request,
            @RequestHeader(value = RequestIds.HEADER, required = false) String key) {
        return shiftService.updateShift(principal.getName(), id, request, RequestIds.parse(key));
    }

    @PatchMapping("/{id}/status")
    public ShiftResponse changeStatus(Principal principal, @PathVariable Long id,
            @Valid @RequestBody ShiftStatusRequest request,
            @RequestHeader(value = RequestIds.HEADER, required = false) String key) {
        return shiftService.changeStatus(principal.getName(), id, request, RequestIds.parse(key));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteShift(Principal principal, @PathVariable Long id) {
        String batch = shiftService.deleteShift(principal.getName(), id);
        return ResponseEntity.noContent().header("X-Delete-Batch", batch).build();
    }

    @GetMapping("/pay-periods")
    public com.angel.flexbuddy.dto.PayPeriodsResponse payPeriods(Principal principal,
            @RequestParam(defaultValue = "2") int count) {
        return reportService.payPeriods(principal.getName(), count);
    }

    @GetMapping("/goals")
    public com.angel.flexbuddy.dto.GoalsResponse goals(Principal principal) {
        return reportService.goals(principal.getName());
    }

    @GetMapping("/missing-miles")
    public List<ShiftResponse> missingMiles(Principal principal, @RequestParam(defaultValue = "7") int days) {
        return shiftService.missingMiles(principal.getName(), days);
    }

    @GetMapping("/{id:\\d+}")
    public ShiftResponse getShift(Principal principal, @PathVariable Long id) {
        return shiftService.getShift(principal.getName(), id);
    }

    @GetMapping("/odometer/latest")
    public ResponseEntity<OdometerReadingResponse> latestOdometer(Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) java.time.LocalDateTime before) {
        return shiftService.latestOdometer(principal.getName(), before)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @PostMapping("/{id}/start")
    public ShiftResponse startShift(Principal principal, @PathVariable Long id) {
        return shiftService.startShift(principal.getName(), id);
    }

    @PostMapping("/{id}/restore")
    public ShiftResponse restoreShift(Principal principal, @PathVariable Long id) {
        return shiftService.restoreShift(principal.getName(), id);
    }

    @PostMapping("/restore-batch/{batchId}")
    public java.util.Map<String, Integer> restoreBatch(Principal principal, @PathVariable String batchId) {
        return java.util.Map.of("restored", shiftService.restoreBatch(principal.getName(), batchId));
    }

    @GetMapping("/trash")
    public List<ShiftResponse> getTrash(Principal principal) {
        return shiftService.getTrash(principal.getName());
    }

    @DeleteMapping("/trash/{id}")
    public ResponseEntity<Void> permanentlyDelete(Principal principal, @PathVariable Long id) {
        shiftService.permanentlyDelete(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/trash")
    public java.util.Map<String, Integer> emptyTrash(Principal principal) {
        return java.util.Map.of("deleted", shiftService.emptyTrash(principal.getName()));
    }

    @GetMapping(value = "/export.csv", produces = "text/csv")
    public ResponseEntity<StreamingResponseBody> exportCsv(Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String station,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String sort,
            @RequestParam(name = "dir", required = false) String direction,
            @RequestParam(required = false) String status) {
        ShiftFilter filter = ShiftFilter.of(from, to, station, query, sort, direction)
                .withStatuses(ShiftStatus.parseSet(status, ShiftStatus.EARNINGS));
        List<ShiftResponse> shifts = shiftService.getShifts(principal.getName(), filter);
        StreamingResponseBody body = output -> csvWriter.write(shifts, output);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exportFilename(from, to) + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }

    private String exportFilename(LocalDate from, LocalDate to) {
        if (from != null || to != null) {
            String start = from == null ? "start" : from.toString();
            String end = to == null ? "today" : to.toString();
            return "flexbuddy-shifts-" + start + "_to_" + end + ".csv";
        }
        return "flexbuddy-shifts-" + LocalDate.now(clock).format(DateTimeFormatter.ISO_DATE) + ".csv";
    }
}
