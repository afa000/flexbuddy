package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.stereotype.Controller;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.angel.flexbuddy.dto.RegistrationRequest;
import com.angel.flexbuddy.dto.AccountBackupFile;
import com.angel.flexbuddy.dto.RestorePreviewResponse;
import com.angel.flexbuddy.dto.RestoreRequest;
import com.angel.flexbuddy.dto.RestoreResult;
import com.angel.flexbuddy.service.AccountService;
import com.angel.flexbuddy.service.AccountBackupService;
import com.angel.flexbuddy.service.AccountRestoreService;
import com.angel.flexbuddy.repository.AppUserRepository;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpSession;

import jakarta.validation.Valid;

@Controller
public class AccountController {

    private final AccountService accountService;
    private final AccountBackupService backupService;
    private final AccountRestoreService restoreService;
    private final AppUserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    public AccountController(AccountService accountService, AccountBackupService backupService,
            AccountRestoreService restoreService, AppUserRepository userRepository, ObjectMapper objectMapper,
            Clock clock) {
        this.accountService = accountService;
        this.backupService = backupService;
        this.restoreService = restoreService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @GetMapping("/login")
    public String loginPage() {
        return "login";
    }

    @GetMapping("/register")
    public String registrationPage(Model model) {
        model.addAttribute("registration", new RegistrationRequest());
        return "register";
    }

    @PostMapping("/register")
    public String register(
            @Valid @ModelAttribute("registration") RegistrationRequest registration,
            BindingResult bindingResult
    ) {
        if (accountService.emailIsRegistered(registration.getEmail())) {
            bindingResult.rejectValue("email", "email.registered", "An account already uses this email.");
        }

        if (bindingResult.hasErrors()) {
            return "register";
        }

        accountService.register(registration);
        return "redirect:/login?registered";
    }

    @GetMapping("/account")
    public String accountPage(Principal principal, Model model) {
        userRepository.findByEmailIgnoreCase(principal.getName())
                .ifPresent(user -> model.addAttribute("currentUser", user));
        return "account";
    }

    @GetMapping("/account/backup")
    public ResponseEntity<byte[]> downloadBackup(Principal principal) throws java.io.IOException {
        AccountBackupFile backup = backupService.create(principal.getName());
        byte[] content = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(backup);
        String filename = "flexbuddy-backup-" + LocalDate.now(clock) + ".json";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(content);
    }

    @PostMapping(value = "/account/restore/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<RestorePreviewResponse> previewRestore(Principal principal,
            @RequestParam("backup") MultipartFile backup, HttpSession session) {
        return ResponseEntity.ok(restoreService.preview(principal.getName(), backup, session));
    }

    @PostMapping("/account/restore")
    public ResponseEntity<RestoreResult> restore(Principal principal, @Valid @RequestBody RestoreRequest request,
            HttpSession session) {
        return ResponseEntity.ok(restoreService.restore(principal.getName(), request, session));
    }

    @PostMapping("/account/restore/{batchId}/undo")
    public ResponseEntity<java.util.Map<String, Integer>> undoRestore(Principal principal,
            @PathVariable String batchId) {
        return ResponseEntity.ok(restoreService.undoReplace(principal.getName(), batchId));
    }
}
