package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;

@Controller
public class PageController {

    private final AppUserRepository userRepository;
    private final ShiftRepository shiftRepository;
    private final Clock clock;

    public PageController(AppUserRepository userRepository, ShiftRepository shiftRepository, Clock clock) {
        this.userRepository = userRepository;
        this.shiftRepository = shiftRepository;
        this.clock = clock;
    }

    @GetMapping("/")
    public String shiftsPage(Principal principal, Model model) {
        userRepository.findByEmailIgnoreCase(principal.getName())
                .ifPresent(user -> {
                    model.addAttribute("currentUser", user);
                    boolean backupDue = shiftRepository.countByOwnerEmailIgnoreCase(user.getEmail()) > 10
                            && (user.getLastBackupAt() == null
                            || user.getLastBackupAt().isBefore(Instant.now(clock).minus(30, ChronoUnit.DAYS)));
                    model.addAttribute("backupDue", backupDue);
                });
        return "shifts";
    }
}
