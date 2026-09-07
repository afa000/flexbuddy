package com.angel.flexbuddy.controller;

import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.ShiftImportPreviewResponse;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.ShiftStatisticsResponse;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.service.ShiftImportService;
import com.angel.flexbuddy.service.ShiftService;
import jakarta.validation.Valid;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;







@RestController
@RequestMapping("/shifts")
public class ShiftController {

    private final ShiftService shiftService;
    private final ShiftImportService shiftImportService;

    public ShiftController(ShiftService shiftService, ShiftImportService shiftImportService) {
        this.shiftService = shiftService;
        this.shiftImportService = shiftImportService;
    }
    
    @GetMapping
    public List<ShiftResponse> getAllShifts() {
        return shiftService.getAllShifts();
    }

    @GetMapping("/statistics")
    public ShiftStatisticsResponse getShiftStatistics() {
        return shiftService.getShiftStatistics();
    }
    

    @PostMapping
    public ShiftResponse createShift(@Valid @RequestBody CreateShiftRequest request) {
        return shiftService.createShift(request);
    }

    @PostMapping(value = "/import-preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ShiftImportPreviewResponse importPreview(@RequestParam("screenshot") MultipartFile screenshot) {

        return shiftImportService.createPreview(screenshot);

    }

    
    @PutMapping("/{id}")
    public ShiftResponse updateShift(@PathVariable Long id, @Valid @RequestBody UpdateShiftRequest request) {
        return shiftService.updateShift(id, request);
    }

    @DeleteMapping("/{id}")
    public void deleteShift(@PathVariable Long id) {
        shiftService.deleteShift(id);
    }

}
