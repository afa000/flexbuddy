package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@Controller
public class PageController {

    private final AppUserRepository userRepository;
    private final ShiftRepository shiftRepository;
    private final Clock clock;
    private final com.angel.flexbuddy.repository.RecoveryCodeRepository recoveryCodeRepository;

    public PageController(AppUserRepository userRepository, ShiftRepository shiftRepository, Clock clock,
            com.angel.flexbuddy.repository.RecoveryCodeRepository recoveryCodeRepository) {
        this.userRepository = userRepository;
        this.shiftRepository = shiftRepository;
        this.clock = clock;
        this.recoveryCodeRepository = recoveryCodeRepository;
    }

    @GetMapping("/")
    public String shiftsPage(Principal principal, Model model) {
        userRepository.findByEmailIgnoreCase(principal.getName())
                .ifPresent(user -> {
                    model.addAttribute("currentUser", user);
                    // Only a driver with two-step sign-in can have used a recovery code, so only they cost a query.
                    if (user.getTwoFactorMethod() != null) {
                        model.addAttribute("recoveryLeft", recoveryCodeRepository.countByOwnerIdAndUsedAtIsNull(user.getId()));
                    }
                    // One count serves the backup reminder and the setup card.
                    long shiftCount = shiftRepository.countByOwnerEmailIgnoreCase(user.getEmail());
                    boolean backupDue = shiftCount > 10
                            && (user.getLastBackupAt() == null
                            || user.getLastBackupAt().isBefore(Instant.now(clock).minus(30, ChronoUnit.DAYS)));
                    model.addAttribute("backupDue", backupDue);
                    model.addAttribute("setupDue", user.getSetupDismissedAt() == null);
                    model.addAttribute("hasBlocks", shiftCount > 0);
                    model.addAttribute("askMissingMiles", user.isAskMissingMiles());
                });
        return "shifts";
    }

    /**
     * Android shares a screenshot here as a multipart POST. The service worker normally answers
     * it on the device; this runs only when no worker controls the page, so the file is ignored
     * and the import screen asks the driver to choose it again. It changes nothing, so it is
     * exempt from CSRF checks, which a share from another app cannot pass.
     */
    @PostMapping("/share-import")
    public String sharedScreenshotWithoutServiceWorker() {
        return "redirect:/?screen=import&shared=unavailable";
    }
}
