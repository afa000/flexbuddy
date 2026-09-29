package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.angel.flexbuddy.repository.AppUserRepository;

/**
 * A fresh security token for the signed-in account, for requests sent after the session was renewed by the
 * remember-me cookie. The account id lets a queued change refuse to be sent under a different account.
 */
@RestController
public class CsrfController {

    private final AppUserRepository userRepository;

    public CsrfController(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/csrf")
    public ResponseEntity<Map<String, Object>> csrf(CsrfToken token, Principal principal) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("headerName", token.getHeaderName());
        body.put("token", token.getToken());
        body.put("accountId", userRepository.findByEmailIgnoreCase(principal.getName()).map(user -> user.getId()).orElse(null));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
