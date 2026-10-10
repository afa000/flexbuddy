package com.angel.flexbuddy.security;

import java.io.IOException;

import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.stereotype.Component;

import com.angel.flexbuddy.controller.EmailVerificationController;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.service.EmailVerificationService;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

/**
 * What happens after the right password. A verified account goes home as before. An unverified one is not let in: the
 * sign-in is undone, along with the remember-me token form login just made, and the driver is sent to enter a code
 * that is emailed right then. Checking only after the password keeps the page from telling a stranger which emails
 * have unconfirmed accounts.
 */
@Component
public class VerifiedLoginSuccessHandler implements AuthenticationSuccessHandler {

    private final AppUserRepository userRepository;
    private final EmailVerificationService verification;
    private final PersistentTokenBasedRememberMeServices rememberMeServices;

    public VerifiedLoginSuccessHandler(AppUserRepository userRepository, EmailVerificationService verification,
            PersistentTokenBasedRememberMeServices rememberMeServices) {
        this.userRepository = userRepository;
        this.verification = verification;
        this.rememberMeServices = rememberMeServices;
    }

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) throws IOException, ServletException {
        AppUser user = userRepository.findByEmailIgnoreCase(authentication.getName()).orElse(null);
        if (user == null || user.isEmailVerified()) {
            response.sendRedirect("/");
            return;
        }
        boolean remember = EmailVerificationController.rememberRequested(request.getParameter("remember-me"));
        rememberMeServices.logout(request, response, authentication);
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        HttpSession session = request.getSession(true);
        session.setAttribute(EmailVerificationController.PENDING_USER, user.getId());
        session.setAttribute(EmailVerificationController.PENDING_REMEMBER, remember);
        verification.startVerification(user, request.getRemoteAddr());
        response.sendRedirect("/verify-email");
    }
}
