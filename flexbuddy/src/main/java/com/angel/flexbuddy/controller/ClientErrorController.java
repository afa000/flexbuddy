package com.angel.flexbuddy.controller;

import java.security.Principal;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.angel.flexbuddy.alert.ErrorAlertService;
import com.angel.flexbuddy.alert.ErrorReports;
import com.angel.flexbuddy.dto.ClientErrorRequest;
import com.angel.flexbuddy.repository.AppUserRepository;

import jakarta.validation.Valid;

/**
 * Receives an uncaught script error from one of the app's own pages. Signed-in and CSRF-protected like every other
 * write. It always answers 204, including when the report was throttled, so a page never learns whether it was sent.
 */
@RestController
public class ClientErrorController {

    private final ErrorAlertService alerts;
    private final AppUserRepository userRepository;

    public ClientErrorController(ErrorAlertService alerts, AppUserRepository userRepository) {
        this.alerts = alerts;
        this.userRepository = userRepository;
    }

    @PostMapping("/client-errors")
    public ResponseEntity<Void> report(@Valid @RequestBody ClientErrorRequest request, Principal principal) {
        userRepository.findByEmailIgnoreCase(principal.getName()).ifPresent(user -> {
            if (alerts.allowBrowserReport(user.getId())) {
                alerts.report(ErrorReports.fromBrowser(request, user.getId()));
            }
        });
        return ResponseEntity.noContent().build();
    }
}
