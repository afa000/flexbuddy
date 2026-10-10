package com.angel.flexbuddy.controller;

import java.util.Locale;
import java.util.Set;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.security.SignInCompleter;
import com.angel.flexbuddy.service.EmailCodeService;
import com.angel.flexbuddy.service.EmailVerificationService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * The Check your email page. A driver who has not confirmed the address is not signed in; the account is remembered in
 * the session until the code is right, and only then is the driver signed in, with remember-me if it was asked for.
 */
@Controller
public class EmailVerificationController {

    public static final String PENDING_USER = "flexbuddy.pendingVerificationUser";
    public static final String PENDING_REMEMBER = "flexbuddy.pendingVerificationRemember";

    private static final Set<String> TRUE_VALUES = Set.of("true", "on", "yes", "1");

    private final EmailVerificationService verification;
    private final AppUserRepository userRepository;
    private final SignInCompleter signInCompleter;

    public EmailVerificationController(EmailVerificationService verification, AppUserRepository userRepository,
            SignInCompleter signInCompleter) {
        this.verification = verification;
        this.userRepository = userRepository;
        this.signInCompleter = signInCompleter;
    }

    /** Whether a remember-me or display-mode value means the driver wants to stay signed in. */
    public static boolean rememberRequested(String value) {
        return value != null && TRUE_VALUES.contains(value.trim().toLowerCase(Locale.ROOT));
    }

    /** Keeps the first character and the domain: a•••• at gmail.com. */
    static String mask(String email) {
        int at = email.indexOf('@');
        if (at < 1) {
            return email;
        }
        return email.charAt(0) + "••••" + email.substring(at);
    }

    @GetMapping("/verify-email")
    public String page(HttpServletRequest request, Model model) {
        AppUser user = pending(request);
        if (user == null) {
            return "redirect:/login";
        }
        model.addAttribute("maskedEmail", mask(user.getEmail()));
        return "verify-email";
    }

    @PostMapping("/verify-email")
    public String verify(@RequestParam(name = "code", defaultValue = "") String code, HttpServletRequest request,
            HttpServletResponse response, Model model) {
        AppUser user = pending(request);
        if (user == null) {
            return "redirect:/login";
        }
        EmailCodeService.CheckResult result = verification.verify(user.getId(), code);
        if (result == EmailCodeService.CheckResult.OK) {
            signIn(user, request, response);
            return "redirect:/?welcome";
        }
        model.addAttribute("maskedEmail", mask(user.getEmail()));
        model.addAttribute("error", switch (result) {
            case EXPIRED -> "error.code.expired";
            case TOO_MANY -> "error.verify.tooManyWrong";
            default -> "error.twoFactor.codeWrongEmail";
        });
        return "verify-email";
    }

    @PostMapping("/verify-email/resend")
    public String resend(HttpServletRequest request, Model model) {
        AppUser user = pending(request);
        if (user == null) {
            return "redirect:/login";
        }
        if (verification.startVerification(user, request.getRemoteAddr()) == EmailCodeService.SendResult.LIMITED) {
            model.addAttribute("maskedEmail", mask(user.getEmail()));
            model.addAttribute("error", "error.code.tooManyRequested");
            return "verify-email";
        }
        return "redirect:/verify-email?resent";
    }

    /** "Use a different email": forgets the pending sign-up and goes back to the form. */
    @PostMapping("/verify-email/cancel")
    public String cancel(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(PENDING_USER);
            session.removeAttribute(PENDING_REMEMBER);
        }
        return "redirect:/register";
    }

    private AppUser pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(PENDING_USER) instanceof Long id)) {
            return null;
        }
        return userRepository.findById(id).orElse(null);
    }

    private void signIn(AppUser user, HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        boolean remember = session != null && Boolean.TRUE.equals(session.getAttribute(PENDING_REMEMBER));
        signInCompleter.complete(request, response, user, remember);
        HttpSession current = request.getSession(false);
        if (current != null) {
            current.removeAttribute(PENDING_USER);
            current.removeAttribute(PENDING_REMEMBER);
        }
    }
}
