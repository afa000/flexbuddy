package com.angel.flexbuddy.api;

import java.security.Principal;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import com.angel.flexbuddy.repository.AppUserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

@RestController
@RequestMapping("/api/v1")
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiAuthController {
    private final ApiSignInService signIn;
    private final AppTokenService tokens;
    private final AppUserRepository users;
    public ApiAuthController(ApiSignInService signIn, AppTokenService tokens, AppUserRepository users) {
        this.signIn = signIn;
        this.tokens = tokens;
        this.users = users;
    }
    // Explicit redaction also protects against accidental diagnostic toString() calls.
    public record SignIn(@NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 72) String password, @Size(max = 80) String deviceName) {
        @Override public String toString() { return "SignIn[redacted]"; }
    }
    public record Code(@NotBlank @Size(max = 47) String challenge, @NotBlank @Size(max = 32) String code) {
        @Override public String toString() { return "Code[redacted]"; }
    }
    public record Resend(@NotBlank @Size(max = 47) String challenge) {
        @Override public String toString() { return "Resend[redacted]"; }
    }
    @PostMapping("/auth/sign-in")
    ResponseEntity<?> signIn(@Valid @RequestBody SignIn body, HttpServletRequest request) {
        if (body.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            return invalid();
        }
        return signIn.signIn(body.email(), body.password(), body.deviceName(), request);
    }
    @PostMapping("/auth/code")
    ResponseEntity<?> code(@Valid @RequestBody Code body, HttpServletRequest request) {
        return signIn.code(body.challenge(), body.code(), request.getRemoteAddr());
    }
    @PostMapping("/auth/code/resend")
    ResponseEntity<?> resend(@Valid @RequestBody Resend body, HttpServletRequest request) {
        return signIn.resend(body.challenge(), request.getRemoteAddr());
    }
    @PostMapping("/auth/sign-out")
    ResponseEntity<?> signOut(HttpServletRequest request) {
        tokens.signOut((Long) request.getAttribute(ApiTokenFilter.TOKEN_ID));
        return ResponseEntity.noContent().build();
    }
    @GetMapping("/account")
    ResponseEntity<?> account(Principal principal) {
        return users.findByEmailIgnoreCase(principal.getName())
                .<ResponseEntity<?>>map(u -> ResponseEntity.ok(ApiAccount.of(u)))
                .orElseGet(() -> ResponseEntity.status(401).body(Map.of("error", "unauthorized")));
    }
    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    ResponseEntity<?> invalid() {
        return ResponseEntity.badRequest().body(Map.of("error", "invalid"));
    }
}
