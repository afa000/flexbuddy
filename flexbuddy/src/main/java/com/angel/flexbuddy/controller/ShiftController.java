package com.angel.flexbuddy.controller;

import java.util.List;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.angel.flexbuddy.dto.CreateShiftRequest;
import com.angel.flexbuddy.dto.ShiftResponse;
import com.angel.flexbuddy.dto.UpdateShiftRequest;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.service.ShiftService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;






@RestController
@RequestMapping("/shifts")
public class ShiftController {

    private final ShiftService shiftService;

    public ShiftController(ShiftService shiftService) {
        this.shiftService = shiftService;
    }
    
    @GetMapping
    public List<ShiftResponse> getAllShifts() {
        return shiftService.getAllShifts();
    }

    @PostMapping
    public ShiftResponse createShift(@Valid @RequestBody CreateShiftRequest request) {
        return shiftService.createShift(request);
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
