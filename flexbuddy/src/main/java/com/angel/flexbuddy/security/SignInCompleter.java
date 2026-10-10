package com.angel.flexbuddy.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.stereotype.Component;

import com.angel.flexbuddy.model.AppUser;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Signs a driver in once every step has passed: the emailed code that confirms a new address, or the second code of
 * two-step sign-in. It starts a fresh session id so an old one cannot be reused, saves the authentication, and issues
 * the remember-me token when the driver asked to stay signed in.
 */
@Component
public class SignInCompleter {

    private final UserDetailsService userDetailsService;
    private final PersistentTokenBasedRememberMeServices rememberMeServices;

    public SignInCompleter(UserDetailsService userDetailsService,
            PersistentTokenBasedRememberMeServices rememberMeServices) {
        this.userDetailsService = userDetailsService;
        this.rememberMeServices = rememberMeServices;
    }

    public void complete(HttpServletRequest request, HttpServletResponse response, AppUser user, boolean remember) {
        request.changeSessionId();
        UserDetails details = userDetailsService.loadUserByUsername(user.getEmail());
        Authentication authentication = UsernamePasswordAuthenticationToken.authenticated(
                details, null, details.getAuthorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        new HttpSessionSecurityContextRepository().saveContext(context, request, response);
        if (remember) {
            rememberMeServices.loginSuccess(new RememberMeRequest(request), response, authentication);
        }
    }

    /** Presents the request as one that ticked Keep me signed in, which is what the remember-me service looks for. */
    private static final class RememberMeRequest extends HttpServletRequestWrapper {
        RememberMeRequest(HttpServletRequest request) {
            super(request);
        }

        @Override
        public String getParameter(String name) {
            return "remember-me".equals(name) ? "true" : super.getParameter(name);
        }
    }
}
