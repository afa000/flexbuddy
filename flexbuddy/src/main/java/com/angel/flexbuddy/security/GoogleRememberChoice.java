package com.angel.flexbuddy.security;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * Carries Keep me signed in through Google. The choice is made on the page that links to Google, but the request that
 * finishes the sign-in is Google's callback, which has no such field, so it is noted in the session on the way out.
 */
@Component
@ConditionalOnExpression("!'${flexbuddy.google.client-id:}'.isBlank()")
public class GoogleRememberChoice implements OAuth2AuthorizationRequestResolver {

    /** The session attribute that says Keep me signed in was chosen before going to Google. */
    public static final String GOOGLE_REMEMBER = "flexbuddy.googleRemember";

    private final DefaultOAuth2AuthorizationRequestResolver delegate;

    public GoogleRememberChoice(ClientRegistrationRepository registrations) {
        this.delegate = new DefaultOAuth2AuthorizationRequestResolver(registrations, "/oauth2/authorization");
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
        OAuth2AuthorizationRequest resolved = delegate.resolve(request);
        note(request, resolved);
        return resolved;
    }

    @Override
    public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
        OAuth2AuthorizationRequest resolved = delegate.resolve(request, clientRegistrationId);
        note(request, resolved);
        return resolved;
    }

    private static void note(HttpServletRequest request, OAuth2AuthorizationRequest resolved) {
        if (resolved == null) {
            return;
        }
        HttpSession session = request.getSession(true);
        if (request.getParameter("remember") != null) {
            session.setAttribute(GOOGLE_REMEMBER, Boolean.TRUE);
        } else {
            session.removeAttribute(GOOGLE_REMEMBER);
        }
    }
}
