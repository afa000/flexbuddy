package com.angel.flexbuddy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;

import com.angel.flexbuddy.dto.RegistrationRequest;
import com.angel.flexbuddy.service.AccountService;

import jakarta.validation.Valid;

@Controller
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
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
}
