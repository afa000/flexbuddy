package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.angel.flexbuddy.dto.TaxPaymentRequest;
import com.angel.flexbuddy.dto.TaxPaymentResponse;
import com.angel.flexbuddy.dto.TaxSummaryResponse;
import com.angel.flexbuddy.service.TaxService;
import com.angel.flexbuddy.service.UserTimeService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/tax")
public class TaxController {

    private final TaxService taxService;
    private final UserTimeService userTime;

    public TaxController(TaxService taxService, UserTimeService userTime) {
        this.taxService = taxService;
        this.userTime = userTime;
    }

    @GetMapping("/summary")
    public TaxSummaryResponse summary(Principal principal, @RequestParam(required = false) Integer year) {
        return taxService.summary(principal.getName(), year(principal, year));
    }

    @GetMapping("/summary.csv")
    public ResponseEntity<StreamingResponseBody> summaryCsv(Principal principal, @RequestParam(required = false) Integer year) {
        String email = principal.getName();
        int taxYear = year(principal, year);
        StreamingResponseBody body = output -> taxService.writeYearCsv(email, taxYear, output);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flexbuddy-tax-summary-" + taxYear + ".csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(body);
    }

    @PostMapping("/payments")
    public TaxPaymentResponse addPayment(Principal principal, @Valid @RequestBody TaxPaymentRequest request) {
        return taxService.addPayment(principal.getName(), request);
    }

    @DeleteMapping("/payments/{id}")
    public ResponseEntity<Void> deletePayment(Principal principal, @PathVariable Long id) {
        taxService.deletePayment(principal.getName(), id);
        return ResponseEntity.noContent().build();
    }

    private int year(Principal principal, Integer year) {
        return year == null ? userTime.today(principal.getName()).getYear() : year;
    }
}
