package com.angel.flexbuddy.controller;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.TaxService;
import com.angel.flexbuddy.service.UserTimeService;

/** The printable tax year summary. It is a page rather than JSON, so it lives apart from the tax API. */
@Controller
public class TaxPageController {

    private final TaxService taxService;
    private final UserTimeService userTime;
    private final AppUserRepository userRepository;

    public TaxPageController(TaxService taxService, UserTimeService userTime, AppUserRepository userRepository) {
        this.taxService = taxService;
        this.userTime = userTime;
        this.userRepository = userRepository;
    }

    @GetMapping("/tax/year-summary")
    public String yearSummary(Principal principal, @RequestParam(required = false) Integer year, Model model) {
        String email = principal.getName();
        int taxYear = year == null ? userTime.today(email).getYear() : year;
        String name = userRepository.findByEmailIgnoreCase(email).map(user -> user.getDisplayName()).orElse(email);
        model.addAttribute("report", taxService.yearReport(email, name, taxYear));
        return "tax-summary";
    }
}
