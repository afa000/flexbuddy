package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.angel.flexbuddy.dto.StandingEntryRequest;
import com.angel.flexbuddy.dto.StandingResponse;
import com.angel.flexbuddy.service.StandingService;

import jakarta.validation.Valid;

/** The Flex standing the driver logged, one entry per day, next to the forfeits and cancellations around it. */
@RestController
@RequestMapping("/standing")
public class StandingController {

    private final StandingService standingService;

    public StandingController(StandingService standingService) {
        this.standingService = standingService;
    }

    @GetMapping
    public StandingResponse window(Principal principal, @RequestParam(defaultValue = "90") int days) {
        return standingService.window(principal.getName(), days);
    }

    @PutMapping("/{recordedOn}")
    public ResponseEntity<Void> log(Principal principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordedOn,
            @Valid @RequestBody StandingEntryRequest request) {
        standingService.log(principal.getName(), recordedOn, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{recordedOn}")
    public ResponseEntity<Void> remove(Principal principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate recordedOn) {
        standingService.remove(principal.getName(), recordedOn);
        return ResponseEntity.noContent().build();
    }
}
