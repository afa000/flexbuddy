package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.EarningsReportResponse;
import com.angel.flexbuddy.dto.GroupBy;
import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.dto.ShiftImportPreviewResponse;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.ShiftStatisticsResponse;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.service.ShiftImportService;
import com.angel.flexbuddy.service.ShiftReportService;
import com.angel.flexbuddy.service.ShiftService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/shifts")
public class ShiftController {

    private final ShiftService shiftService;
    private final ShiftReportService reportService;
    private final ShiftImportService shiftImportService;

    public ShiftController(ShiftService shiftService, ShiftReportService reportService, ShiftImportService shiftImportService) {
        this.shiftService = shiftService;
        this.reportService = reportService;
        this.shiftImportService = shiftImportService;
    }

    @GetMapping
    public List<ShiftResponse> getShifts(Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String station,
            @RequestParam(name = "q", required = false) String query,
            @RequestParam(required = false) String sort,
            @RequestParam(name = "dir", required = false) String direction) {
        return shiftService.getShifts(principal.getName(), ShiftFilter.of(from, to, station, query, sort, direction));
    }

    @GetMapping("/statistics")
    public ShiftStatisticsResponse getStatistics(Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String station,
            @RequestParam(name = "q", required = false) String query) {
        return reportService.statistics(principal.getName(), ShiftFilter.report(from, to, station, query));
    }

    @GetMapping("/stations")
    public List<String> getStations(Principal principal) {
        return shiftService.getStations(principal.getName());
    }

    @GetMapping("/reports/earnings")
    public EarningsReportResponse getEarningsReport(Principal principal,
            @RequestParam String groupBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String station,
            @RequestParam(name = "q", required = false) String query) {
        return reportService.earnings(principal.getName(), ShiftFilter.report(from, to, station, query), GroupBy.parse(groupBy));
    }

    @PostMapping
    public ShiftResponse createShift(Principal principal, @Valid @RequestBody CreateShiftRequest request) {
        return shiftService.createShift(principal.getName(), request);
    }

    @PostMapping(value = "/import-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ShiftImportPreviewResponse importPreview(@RequestParam("screenshot") MultipartFile screenshot) {
        return shiftImportService.createPreview(screenshot);
    }

    @PutMapping("/{id}")
    public ShiftResponse updateShift(Principal principal, @PathVariable Long id,
            @Valid @RequestBody UpdateShiftRequest request) {
        return shiftService.updateShift(principal.getName(), id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteShift(Principal principal, @PathVariable Long id) {
        shiftService.deleteShift(principal.getName(), id);
    }
}
