package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import com.angel.flexbuddy.dto.ExpenseFilter;
import com.angel.flexbuddy.dto.ExpenseRequest;
import com.angel.flexbuddy.dto.ExpenseResponse;
import com.angel.flexbuddy.dto.ExpenseSummaryResponse;
import com.angel.flexbuddy.service.ExpenseCsvWriter;
import com.angel.flexbuddy.service.ExpenseService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/expenses")
public class ExpenseController {
    private final ExpenseService expenseService;
    private final ExpenseCsvWriter csvWriter;
    private final Clock clock;

    public ExpenseController(ExpenseService expenseService, ExpenseCsvWriter csvWriter, Clock clock) {
        this.expenseService = expenseService;
        this.csvWriter = csvWriter;
        this.clock = clock;
    }

    @GetMapping
    public List<ExpenseResponse> list(Principal principal,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) String station, @RequestParam(name="q", required=false) String query,
            @RequestParam(required=false) String category, @RequestParam(required=false) Long shiftId) {
        return expenseService.getExpenses(principal.getName(), ExpenseFilter.of(from,to,station,query,category,shiftId));
    }

    @GetMapping("/summary")
    public ExpenseSummaryResponse summary(Principal principal,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) String station, @RequestParam(name="q", required=false) String query,
            @RequestParam(required=false) String category, @RequestParam(required=false) Long shiftId) {
        return expenseService.summary(principal.getName(), ExpenseFilter.of(from,to,station,query,category,shiftId));
    }

    @PostMapping public ExpenseResponse create(Principal p, @Valid @RequestBody ExpenseRequest r) { return expenseService.create(p.getName(), r); }
    @PutMapping("/{id}") public ExpenseResponse update(Principal p, @PathVariable Long id, @Valid @RequestBody ExpenseRequest r) { return expenseService.update(p.getName(), id, r); }
    @DeleteMapping("/{id}") public ResponseEntity<Void> delete(Principal p, @PathVariable Long id) {
        return ResponseEntity.noContent().header("X-Delete-Batch", expenseService.delete(p.getName(), id)).build();
    }
    @PostMapping("/{id}/restore") public ExpenseResponse restore(Principal p, @PathVariable Long id) { return expenseService.restore(p.getName(), id); }
    @PostMapping("/restore-batch/{batch}") public Map<String,Integer> restoreBatch(Principal p, @PathVariable String batch) { return Map.of("restored", expenseService.restoreBatch(p.getName(), batch)); }
    @GetMapping("/trash") public List<ExpenseResponse> trash(Principal p) { return expenseService.getTrash(p.getName()); }
    @DeleteMapping("/trash/{id}") public ResponseEntity<Void> permanentlyDelete(Principal p, @PathVariable Long id) { expenseService.permanentlyDelete(p.getName(), id); return ResponseEntity.noContent().build(); }
    @DeleteMapping("/trash") public Map<String,Integer> emptyTrash(Principal p) { return Map.of("deleted", expenseService.emptyTrash(p.getName())); }

    @GetMapping(value="/export.csv", produces="text/csv")
    public ResponseEntity<StreamingResponseBody> export(Principal principal,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required=false) String station, @RequestParam(name="q", required=false) String query,
            @RequestParam(required=false) String category, @RequestParam(required=false) Long shiftId) {
        var rows = expenseService.getExpenses(principal.getName(), ExpenseFilter.of(from,to,station,query,category,shiftId));
        StreamingResponseBody body = output -> csvWriter.write(rows, output);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"flexbuddy-expenses-" + LocalDate.now(clock) + ".csv\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store").body(body);
    }
}
