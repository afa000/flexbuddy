package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.angel.flexbuddy.dto.PayoutDepositRequest;
import com.angel.flexbuddy.service.PayoutService;

import jakarta.validation.Valid;

/** What landed for each payout; the comparison with earned pay comes back in {@code GET /shifts/pay-periods}. */
@RestController
@RequestMapping("/payouts")
public class PayoutController {

    private final PayoutService payoutService;

    public PayoutController(PayoutService payoutService) {
        this.payoutService = payoutService;
    }

    @PutMapping("/{payoutDate}")
    public ResponseEntity<Void> record(Principal principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payoutDate,
            @Valid @RequestBody PayoutDepositRequest request) {
        payoutService.record(principal.getName(), payoutDate, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{payoutDate}")
    public ResponseEntity<Void> remove(Principal principal,
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate payoutDate) {
        payoutService.remove(principal.getName(), payoutDate);
        return ResponseEntity.noContent().build();
    }
}
