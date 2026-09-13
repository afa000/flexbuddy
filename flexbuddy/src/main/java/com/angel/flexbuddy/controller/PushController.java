package com.angel.flexbuddy.controller;

import java.security.Principal;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.angel.flexbuddy.dto.PushConfigResponse;
import com.angel.flexbuddy.dto.PushSubscriptionRequest;
import com.angel.flexbuddy.dto.PushUnsubscribeRequest;
import com.angel.flexbuddy.service.PushService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/push")
public class PushController {

    private final PushService pushService;

    public PushController(PushService pushService) {
        this.pushService = pushService;
    }

    @GetMapping("/public-key")
    public PushConfigResponse publicKey() {
        return pushService.config();
    }

    @PostMapping("/subscriptions")
    public ResponseEntity<Void> subscribe(Principal principal, @Valid @RequestBody PushSubscriptionRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {
        pushService.subscribe(principal.getName(), request, userAgent);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/subscriptions")
    public ResponseEntity<Void> unsubscribe(Principal principal, @Valid @RequestBody PushUnsubscribeRequest request) {
        pushService.unsubscribe(principal.getName(), request.endpoint());
        return ResponseEntity.noContent().build();
    }
}
