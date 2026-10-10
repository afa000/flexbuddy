package com.angel.flexbuddy.security;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;

/**
 * Turns a Google sign-in into a FlexBuddy account. A Google ID already linked to an account signs into it; otherwise an
 * account with the same address is linked, and otherwise a new one is created, already verified and without a
 * password. The principal that comes back is named by the FlexBuddy email, because every controller finds the driver
 * by that name. Google is never asked for anything beyond name, address and a stable ID.
 */
@Component
@ConditionalOnExpression("!'${flexbuddy.google.client-id:}'.isBlank()")
public class GoogleAccountService extends OidcUserService {

    public static final String UNVERIFIED_EMAIL = "unverified_email";
    public static final String LINKED_ELSEWHERE = "linked_elsewhere";
    public static final String TOO_MANY_SIGNUPS = "too_many_signups";

    private static final int MAX_NAME = 80;

    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AttemptLimiter limiter;
    private final SecureRandom random = new SecureRandom();

    public GoogleAccountService(AppUserRepository userRepository, PasswordEncoder passwordEncoder,
            AttemptLimiter limiter) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
    }

    @Override
    public OidcUser loadUser(OidcUserRequest request) {
        OidcUser google = super.loadUser(request);
        return link(google, currentAddress());
    }

    /** The account-matching rules, apart from the call to Google, so they can be tested directly. */
    @Transactional
    OidcUser link(OidcUser google, String address) {
        if (!Boolean.TRUE.equals(google.getEmailVerified()) || google.getEmail() == null) {
            throw error(UNVERIFIED_EMAIL);
        }
        String subject = google.getSubject();
        String email = google.getEmail().trim().toLowerCase(Locale.ROOT);

        AppUser user = userRepository.findByGoogleSubject(subject).orElse(null);
        if (user == null) {
            Optional<AppUser> sameAddress = userRepository.findByEmailIgnoreCase(email);
            if (sameAddress.isPresent()) {
                user = sameAddress.get();
                if (user.getGoogleSubject() != null && !user.getGoogleSubject().equals(subject)) {
                    throw error(LINKED_ELSEWHERE);
                }
                user.setGoogleSubject(subject);
                if (!user.isEmailVerified()) {
                    // Someone registered this address without owning it, so the password they chose must stop working.
                    user.setPasswordHash(unusablePassword());
                    user.setPasswordSet(false);
                    user.setEmailVerified(true);
                }
            } else {
                if (address != null && limiter.isLocked(AttemptLimiter.REGISTER_IP, address)) {
                    throw error(TOO_MANY_SIGNUPS);
                }
                if (address != null) {
                    limiter.record(AttemptLimiter.REGISTER_IP, address);
                }
                user = new AppUser(displayName(google, email), email, unusablePassword());
                user.setPasswordSet(false);
                user.setEmailVerified(true);
                user.setGoogleSubject(subject);
            }
            userRepository.save(user);
        }
        // The account is found by Google's ID, so its own address stays whatever it is, and that is the principal name.
        AppUser resolved = user;
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), google.getIdToken(),
                google.getUserInfo(), "email") {
            @Override
            public String getName() {
                return userEmail(google, resolved);
            }
        };
    }

    private static String userEmail(OidcUser google, AppUser user) {
        return user.getEmail() != null ? user.getEmail() : google.getEmail();
    }

    private String displayName(OidcUser google, String email) {
        String name = firstNonBlank(google.getGivenName(), google.getFullName());
        if (name == null) {
            name = email.substring(0, email.indexOf('@'));
        }
        return name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    /** A random password nobody knows, stored only as a hash: the account exists but cannot be signed into with one. */
    private String unusablePassword() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return passwordEncoder.encode(Base64.getEncoder().encodeToString(bytes));
    }

    private static OAuth2AuthenticationException error(String code) {
        return new OAuth2AuthenticationException(new OAuth2Error(code));
    }

    private static String currentAddress() {
        return RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest().getRemoteAddr() : null;
    }
}
