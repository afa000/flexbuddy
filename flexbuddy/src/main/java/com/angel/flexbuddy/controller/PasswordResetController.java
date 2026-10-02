package com.angel.flexbuddy.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.angel.flexbuddy.dto.ForgotPasswordRequest;
import com.angel.flexbuddy.dto.ResetPasswordRequest;
import com.angel.flexbuddy.exception.InvalidResetTokenException;
import com.angel.flexbuddy.service.PasswordResetService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

/** The forgotten-password pages: asking for a link, and choosing a new password from it. */
@Controller
public class PasswordResetController {

    private final PasswordResetService resetService;

    public PasswordResetController(PasswordResetService resetService) {
        this.resetService = resetService;
    }

    @GetMapping("/forgot-password")
    public String forgotPasswordPage(Model model) {
        model.addAttribute("forgot", new ForgotPasswordRequest());
        return "forgot-password";
    }

    /** Always the same answer, whether or not the email has an account. */
    @PostMapping("/forgot-password")
    public String requestReset(@Valid @ModelAttribute("forgot") ForgotPasswordRequest forgot,
            BindingResult bindingResult, HttpServletRequest request) {
        if (bindingResult.hasErrors()) {
            return "forgot-password";
        }
        resetService.requestReset(forgot.getEmail(), request.getRemoteAddr());
        return "redirect:/forgot-password?sent";
    }

    @GetMapping("/reset-password")
    public String resetPasswordPage(@RequestParam(name = "token", required = false) String token, Model model,
            HttpServletResponse response) {
        keepTheLinkPrivate(response);
        if (resetService.checkToken(token).isEmpty()) {
            return "reset-password-invalid";
        }
        ResetPasswordRequest reset = new ResetPasswordRequest();
        reset.setToken(token);
        model.addAttribute("reset", reset);
        return "reset-password";
    }

    @PostMapping("/reset-password")
    public String resetPassword(@Valid @ModelAttribute("reset") ResetPasswordRequest reset,
            BindingResult bindingResult, HttpServletResponse response) {
        keepTheLinkPrivate(response);
        if (bindingResult.hasErrors()) {
            // The form comes back with the token kept, but only if the link is still good.
            return resetService.checkToken(reset.getToken()).isPresent() ? "reset-password" : "reset-password-invalid";
        }
        try {
            resetService.resetPassword(reset.getToken(), reset.getPassword());
        } catch (InvalidResetTokenException exception) {
            return "reset-password-invalid";
        }
        return "redirect:/login?reset";
    }

    /** The link must not leak through the browser cache or a referrer header. */
    private void keepTheLinkPrivate(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
    }
}
