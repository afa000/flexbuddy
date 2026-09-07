package com.angel.flexbuddy.controller;

import java.security.Principal;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import com.angel.flexbuddy.repository.AppUserRepository;

@Controller
public class PageController {

    private final AppUserRepository userRepository;

    public PageController(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @GetMapping("/")
    public String shiftsPage(Principal principal, Model model) {
        userRepository.findByEmailIgnoreCase(principal.getName())
                .ifPresent(user -> model.addAttribute("currentUser", user));
        return "shifts";
    }
}
