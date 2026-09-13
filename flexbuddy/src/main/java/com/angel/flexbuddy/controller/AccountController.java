package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;

import com.angel.flexbuddy.dto.AccountDeletionRequest;
import com.angel.flexbuddy.dto.RegistrationRequest;
import com.angel.flexbuddy.dto.AccountBackupFile;
import com.angel.flexbuddy.dto.RestorePreviewResponse;
import com.angel.flexbuddy.dto.RestoreRequest;
import com.angel.flexbuddy.dto.RestoreResult;
import com.angel.flexbuddy.service.AccountService;
import com.angel.flexbuddy.service.AccountBackupService;
import com.angel.flexbuddy.service.AccountRestoreService;
import com.angel.flexbuddy.service.AccountSettingsService;
import com.angel.flexbuddy.dto.AccountSettingsRequest;
import com.angel.flexbuddy.dto.AccountSettingsResponse;
import com.angel.flexbuddy.dto.ReminderSettingsRequest;
import com.angel.flexbuddy.dto.TimeZoneRequest;
import com.angel.flexbuddy.exception.InvalidAccountPasswordException;
import com.angel.flexbuddy.repository.AppUserRepository;
import tools.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
    private final AccountSettingsService settingsService;
    private final PersistentTokenBasedRememberMeServices rememberMeServices;

    public AccountController(AccountService accountService, AccountBackupService backupService,
            AccountRestoreService restoreService, AppUserRepository userRepository, ObjectMapper objectMapper,
            Clock clock, AccountSettingsService settingsService,
            PersistentTokenBasedRememberMeServices rememberMeServices) {
        this.accountService = accountService;
        this.backupService = backupService;
        this.restoreService = restoreService;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.settingsService = settingsService;
        this.rememberMeServices = rememberMeServices;
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

        try {
            accountService.register(registration);
        } catch (DataIntegrityViolationException exception) {
            bindingResult.rejectValue("email", "email.registered", "An account already uses this email.");
            return "register";
        }

        return "redirect:/login?registered";
    }

    @GetMapping("/delete-account")
    public String deletionInfoPage() {
        return "delete-account";
    }

    @GetMapping("/account")
    public String accountPage(Principal principal, Model model) {
        addAccountPageModel(principal.getName(), model);
        return "account";
    }

    @DeleteMapping("/account")
    public String deleteAccount(Authentication authentication,
            @Valid @ModelAttribute("deletion") AccountDeletionRequest deletion,
            BindingResult bindingResult, Model model, HttpServletRequest request,
            HttpServletResponse response) {
        if (bindingResult.hasErrors()) {
            addAccountPageModel(authentication.getName(), model);
            return "account";
        }

        try {
            accountService.deleteAccount(authentication.getName(), deletion.getPassword());
        } catch (InvalidAccountPasswordException exception) {
            bindingResult.rejectValue("password", "password.incorrect", exception.getMessage());
            deletion.setPassword(null);
            addAccountPageModel(authentication.getName(), model);
            return "account";
        }

        rememberMeServices.logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?deleted";
    }

    @PostMapping("/account/sign-out-everywhere")
    public String signOutEverywhere(Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) {
        rememberMeServices.logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return "redirect:/login?everywhere";
    }

    @GetMapping("/account/settings")
    @ResponseBody
    public AccountSettingsResponse getSettings(Principal principal) {
        return settingsService.get(principal.getName());
    }

    @PutMapping("/account/settings")
    @ResponseBody
    public AccountSettingsResponse updateSettings(Principal principal,
            @Valid @RequestBody AccountSettingsRequest request) {
        return settingsService.update(principal.getName(), request);
    }

    @PutMapping("/account/reminders")
    @ResponseBody
    public AccountSettingsResponse updateReminders(Principal principal,
            @Valid @RequestBody ReminderSettingsRequest request) {
        return settingsService.updateReminders(principal.getName(), request);
    }

    @PutMapping("/account/time-zone")
    @ResponseBody
    public AccountSettingsResponse updateTimeZone(Principal principal, @Valid @RequestBody TimeZoneRequest request) {
        return settingsService.updateTimeZone(principal.getName(), request);
    }

    @PostMapping("/account/calendar-token")
    @ResponseBody
    public AccountSettingsResponse regenerateCalendarToken(Principal principal) {
        return settingsService.regenerateCalendarToken(principal.getName());
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

    private void addAccountPageModel(String email, Model model) {
        userRepository.findByEmailIgnoreCase(email)
                .ifPresent(user -> model.addAttribute("currentUser", user));
        if (!model.containsAttribute("deletion")) {
            model.addAttribute("deletion", new AccountDeletionRequest());
        }
    }
}
