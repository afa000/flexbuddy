package com.angel.flexbuddy.controller;

import java.security.Principal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.angel.flexbuddy.exception.InvalidAccountPasswordException;
import com.angel.flexbuddy.exception.InvalidTwoFactorCodeException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.TwoFactorMethod;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.security.SignInCompleter;
import com.angel.flexbuddy.service.EmailCodeService;
import com.angel.flexbuddy.service.TwoFactorService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * Two-step sign-in: the Enter your code page that follows a correct password, and the signed-in pages where a driver
 * turns it on or off. Everything is a server-rendered form, so a secret never travels through a script.
 */
@Controller
public class TwoFactorController {

    public static final String PENDING_USER = "flexbuddy.pendingTwoFactorUser";
    public static final String PENDING_REMEMBER = "flexbuddy.pendingTwoFactorRemember";
    public static final String PENDING_SINCE = "flexbuddy.pendingTwoFactorSince";
    static final String SETUP_SECRET = "flexbuddy.twoFactorSetupSecret";
    private static final Duration PENDING_LIFETIME = Duration.ofMinutes(10);

    private final TwoFactorService twoFactor;
    private final AppUserRepository userRepository;
    private final SignInCompleter signInCompleter;
    private final AttemptLimiter limiter;
    private final Clock clock;

    public TwoFactorController(TwoFactorService twoFactor, AppUserRepository userRepository,
            SignInCompleter signInCompleter, AttemptLimiter limiter, Clock clock) {
        this.twoFactor = twoFactor;
        this.userRepository = userRepository;
        this.signInCompleter = signInCompleter;
        this.limiter = limiter;
        this.clock = clock;
    }

    // ---- Signing in ------------------------------------------------------------------------------------------------

    @GetMapping("/sign-in/code")
    public String codePage(HttpServletRequest request, Model model) {
        AppUser user = pending(request);
        if (user == null) {
            return "redirect:/login?expired";
        }
        return codeView(user, model, null);
    }

    @PostMapping("/sign-in/code")
    public String checkCode(@RequestParam(name = "code", defaultValue = "") String code, HttpServletRequest request,
            HttpServletResponse response, Model model) {
        AppUser user = pending(request);
        if (user == null) {
            return "redirect:/login?expired";
        }
        TwoFactorService.Result result = twoFactor.verifySignIn(user, code);
        if (result == TwoFactorService.Result.OK || result == TwoFactorService.Result.OK_RECOVERY) {
            HttpSession session = request.getSession(false);
            boolean remember = session != null && Boolean.TRUE.equals(session.getAttribute(PENDING_REMEMBER));
            signInCompleter.complete(request, response, user, remember);
            clearPending(request);
            return result == TwoFactorService.Result.OK_RECOVERY ? "redirect:/?recovery-used" : "redirect:/";
        }
        // Wrong codes share the sign-in lock with wrong passwords; once it holds, the pending sign-in is dropped.
        if (limiter.isLocked(AttemptLimiter.LOGIN_EMAIL, AttemptLimiter.emailKey(user.getEmail()))) {
            clearPending(request);
            return "redirect:/login?locked";
        }
        return codeView(user, model, result == TwoFactorService.Result.EXPIRED
                ? "That code has expired. Send a new one." : "That code isn't right.");
    }

    @PostMapping("/sign-in/code/resend")
    public String resend(HttpServletRequest request, Model model) {
        AppUser user = pending(request);
        if (user == null) {
            return "redirect:/login?expired";
        }
        if (user.getTwoFactorMethod() == TwoFactorMethod.EMAIL
                && twoFactor.sendSignInEmail(user, request.getRemoteAddr()) == EmailCodeService.SendResult.LIMITED) {
            return codeView(user, model, "Too many codes requested. Try again in an hour.");
        }
        return "redirect:/sign-in/code?resent";
    }

    @PostMapping("/sign-in/code/cancel")
    public String cancel(HttpServletRequest request) {
        clearPending(request);
        return "redirect:/login";
    }

    private String codeView(AppUser user, Model model, String error) {
        model.addAttribute("method", user.getTwoFactorMethod().name());
        model.addAttribute("maskedEmail", EmailVerificationController.mask(user.getEmail()));
        if (error != null) {
            model.addAttribute("error", error);
        }
        return "sign-in-code";
    }

    private AppUser pending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null || !(session.getAttribute(PENDING_USER) instanceof Long id)) {
            return null;
        }
        if (!(session.getAttribute(PENDING_SINCE) instanceof Instant since)
                || since.plus(PENDING_LIFETIME).isBefore(clock.instant())) {
            clearPending(request);
            return null;
        }
        AppUser user = userRepository.findById(id).orElse(null);
        return user != null && user.getTwoFactorMethod() != null ? user : null;
    }

    private static void clearPending(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.removeAttribute(PENDING_USER);
            session.removeAttribute(PENDING_REMEMBER);
            session.removeAttribute(PENDING_SINCE);
        }
    }

    // ---- Settings ----------------------------------------------------------------------------------------------------

    @GetMapping("/account/two-factor")
    public String settings(Principal principal, Model model) {
        return settingsView(user(principal), model, null, null);
    }

    @PostMapping("/account/two-factor/app")
    public String beginApp(Principal principal, HttpServletRequest request, Model model) {
        AppUser user = user(principal);
        TwoFactorService.AppSetup setup = twoFactor.beginApp(user);
        request.getSession(true).setAttribute(SETUP_SECRET, setup.secret());
        model.addAttribute("setup", setup);
        return settingsView(user, model, null, null);
    }

    @PostMapping("/account/two-factor/app/confirm")
    public String confirmApp(@RequestParam(name = "code", defaultValue = "") String code, Principal principal,
            HttpServletRequest request, Model model) {
        AppUser user = user(principal);
        HttpSession session = request.getSession(false);
        String secret = session == null ? null : (String) session.getAttribute(SETUP_SECRET);
        if (secret == null) {
            return settingsView(user, model, "That setup has expired. Start again.", null);
        }
        try {
            List<String> codes = twoFactor.confirmApp(user, secret, code);
            session.removeAttribute(SETUP_SECRET);
            return settingsView(user, model, null, codes);
        } catch (InvalidTwoFactorCodeException exception) {
            // The same key stays on screen so the driver can try the next code without scanning again.
            model.addAttribute("setup", twoFactor.setupFor(user, secret));
            return settingsView(user, model, exception.getMessage(), null);
        }
    }

    @PostMapping("/account/two-factor/email")
    public String beginEmail(Principal principal, HttpServletRequest request, Model model) {
        AppUser user = user(principal);
        if (twoFactor.beginEmail(user, request.getRemoteAddr()) == EmailCodeService.SendResult.LIMITED) {
            return settingsView(user, model, "Too many codes requested. Try again in an hour.", null);
        }
        model.addAttribute("emailPending", true);
        return settingsView(user, model, null, null);
    }

    @PostMapping("/account/two-factor/email/confirm")
    public String confirmEmail(@RequestParam(name = "code", defaultValue = "") String code, Principal principal,
            Model model) {
        AppUser user = user(principal);
        try {
            return settingsView(user, model, null, twoFactor.confirmEmail(user, code));
        } catch (InvalidTwoFactorCodeException exception) {
            model.addAttribute("emailPending", true);
            return settingsView(user, model, exception.getMessage(), null);
        }
    }

    /** For the email method, a code has to be sent before it can be typed into the turn-off or new-codes forms. */
    @PostMapping("/account/two-factor/send-code")
    public String sendCode(Principal principal, HttpServletRequest request, Model model) {
        AppUser user = user(principal);
        if (twoFactor.sendSignInEmail(user, request.getRemoteAddr()) == EmailCodeService.SendResult.LIMITED) {
            return settingsView(user, model, "Too many codes requested. Try again in an hour.", null);
        }
        return "redirect:/account/two-factor?sent";
    }

    @PostMapping("/account/two-factor/recovery-codes")
    public String newRecoveryCodes(@RequestParam(name = "code", defaultValue = "") String code, Principal principal,
            Model model) {
        AppUser user = user(principal);
        try {
            return settingsView(user, model, null, twoFactor.regenerateRecoveryCodes(user, code));
        } catch (InvalidTwoFactorCodeException exception) {
            return settingsView(user, model, exception.getMessage(), null);
        }
    }

    @PostMapping("/account/two-factor/disable")
    public String disable(@RequestParam(name = "password", defaultValue = "") String password,
            @RequestParam(name = "code", defaultValue = "") String code, Principal principal, Model model) {
        AppUser user = user(principal);
        try {
            twoFactor.disable(user, password, code);
            return "redirect:/account/two-factor?off";
        } catch (InvalidAccountPasswordException exception) {
            return settingsView(user, model, "That password isn't right.", null);
        } catch (InvalidTwoFactorCodeException exception) {
            return settingsView(user, model, exception.getMessage(), null);
        }
    }

    private String settingsView(AppUser user, Model model, String error, List<String> recoveryCodes) {
        // A page that just changed the account's state must show the saved state, so it is read again.
        AppUser fresh = userRepository.findById(user.getId()).orElse(user);
        model.addAttribute("currentUser", fresh);
        model.addAttribute("method", fresh.getTwoFactorMethod() == null ? "OFF" : fresh.getTwoFactorMethod().name());
        model.addAttribute("maskedEmail", EmailVerificationController.mask(fresh.getEmail()));
        model.addAttribute("recoveryLeft", twoFactor.recoveryCodesLeft(fresh));
        if (error != null) {
            model.addAttribute("error", error);
        }
        if (recoveryCodes != null) {
            model.addAttribute("recoveryCodes", recoveryCodes);
        }
        return "account-two-factor";
    }

    private AppUser user(Principal principal) {
        return userRepository.findByEmailIgnoreCase(principal.getName())
                .orElseThrow(() -> new IllegalStateException("The signed-in account no longer exists."));
    }
}
