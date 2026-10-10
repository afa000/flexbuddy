package com.angel.flexbuddy.security;

import java.io.IOException;
import java.time.Clock;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.LocaleResolver;

import com.angel.flexbuddy.controller.EmailVerificationController;
import com.angel.flexbuddy.controller.TwoFactorController;
import com.angel.flexbuddy.i18n.UserLocales;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.model.TwoFactorMethod;
import com.angel.flexbuddy.service.EmailVerificationService;
import com.angel.flexbuddy.service.TwoFactorService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * What happens after the right password. A verified account without two-step sign-in goes home as before. Otherwise the
 * sign-in is undone, along with the remember-me token form login just made, and the driver is sent to enter a code:
 * the emailed one that confirms a new address, or the second code of two-step sign-in. Checking only after the password
 * keeps the page from telling a stranger which emails have accounts. A remember-me sign-in never passes through here,
 * which is what lets a trusted device skip the second step.
 */
@Component
public class VerifiedLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AppUserRepository userRepository;
    private final EmailVerificationService verification;
    private final TwoFactorService twoFactor;
    private final PersistentTokenBasedRememberMeServices rememberMeServices;
    private final SignInCompleter signInCompleter;
    private final Clock clock;
    private final LocaleResolver localeResolver;

    public VerifiedLoginSuccessHandler(AppUserRepository userRepository, EmailVerificationService verification,
            TwoFactorService twoFactor, PersistentTokenBasedRememberMeServices rememberMeServices,
            SignInCompleter signInCompleter, Clock clock, LocaleResolver localeResolver) {
        this.userRepository = userRepository;
        this.verification = verification;
        this.twoFactor = twoFactor;
        this.rememberMeServices = rememberMeServices;
        this.signInCompleter = signInCompleter;
        this.clock = clock;
        this.localeResolver = localeResolver;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        AppUser user = userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        // A language the driver chose follows them to a new device, so the pages after sign-in are already in it.
        if (user != null && UserLocales.isSupported(user.getLanguage())) {
            localeResolver.setLocale(request, response, UserLocales.of(user));
        }
        // Form sign-in says it in the remember-me field; Google's callback has none, so its choice was noted in the session.
        HttpSession noted = request.getSession(false);
        boolean googleRemember = noted != null && noted.getAttribute(GoogleRememberChoice.GOOGLE_REMEMBER) != null;
        if (googleRemember) {
            noted.removeAttribute(GoogleRememberChoice.GOOGLE_REMEMBER);
        }
        boolean remember = googleRemember
                || EmailVerificationController.rememberRequested(request.getParameter("remember-me"));
        if (user == null || (user.isEmailVerified() && user.getTwoFactorMethod() == null)) {
            if (googleRemember) {
                signInCompleter.rememberDevice(request, response, authentication);
            }
            response.sendRedirect("/");
            return;
        }
        // Either step undoes the sign-in just made, along with any remember-me token it issued.
        rememberMeServices.logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        HttpSession session = request.getSession(true);
        if (!user.isEmailVerified()) {
            session.setAttribute(EmailVerificationController.PENDING_USER, user.getId());
            session.setAttribute(EmailVerificationController.PENDING_REMEMBER, remember);
            verification.startVerification(user, request.getRemoteAddr());
            response.sendRedirect("/verify-email");
            return;
        }
        session.setAttribute(TwoFactorController.PENDING_USER, user.getId());
        session.setAttribute(TwoFactorController.PENDING_REMEMBER, remember);
        session.setAttribute(TwoFactorController.PENDING_SINCE, clock.instant());
        if (user.getTwoFactorMethod() == TwoFactorMethod.EMAIL) {
            twoFactor.sendSignInEmail(user, request.getRemoteAddr());
        }
        response.sendRedirect("/sign-in/code");
    }
}
