package com.angel.flexbuddy.i18n;

import java.util.Enumeration;
import java.util.List;
import java.util.Locale;

import com.angel.flexbuddy.model.AppUser;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/** The two languages FlexBuddy speaks, and which one a driver's account asks for. */
public final class UserLocales {

    public static final Locale ENGLISH = Locale.ENGLISH;
    public static final Locale SPANISH = Locale.forLanguageTag("es");
    public static final List<Locale> SUPPORTED = List.of(ENGLISH, SPANISH);

    private UserLocales() {
    }

    /** True for the two values an account's language can hold. */
    public static boolean isSupported(String language) {
        return "en".equals(language) || "es".equals(language);
    }

    /** Spanish for any Spanish locale, English for everything else. */
    public static Locale clamp(Locale locale) {
        return locale != null && "es".equals(locale.getLanguage()) ? SPANISH : ENGLISH;
    }

    /** Where the language choice is remembered in the browser. */
    public static final String COOKIE = "fb_lang";

    /**
     * The language a request asks for, outside the servlet's own resolver (the Google callback runs before it): the saved
     * choice if there is one, else the first language in the browser's preference list that we speak, else English.
     */
    public static Locale requested(HttpServletRequest request) {
        if (request == null) return ENGLISH;
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE.equals(cookie.getName()) && cookie.getValue() != null && !cookie.getValue().isBlank()) {
                    return clamp(Locale.forLanguageTag(cookie.getValue().replace('_', '-')));
                }
            }
        }
        return browserPreference(request);
    }

    /** The first language in the browser's preference list that we speak; English when it names none. */
    public static Locale browserPreference(HttpServletRequest request) {
        if (request.getHeader("Accept-Language") == null) return ENGLISH;
        Enumeration<Locale> wanted = request.getLocales();
        while (wanted.hasMoreElements()) {
            String language = wanted.nextElement().getLanguage();
            if ("es".equals(language)) return SPANISH;
            if ("en".equals(language)) return ENGLISH;
        }
        return ENGLISH;
    }

    /** The value an account stores for a request in this language: "es" for Spanish, null (follow the device) otherwise. */
    public static String accountLanguage(Locale locale) {
        return locale != null && "es".equals(locale.getLanguage()) ? "es" : null;
    }

    /** The language of a driver's email, push reminders and calendar: their saved choice, else English. */
    public static Locale of(AppUser user) {
        return user != null && "es".equals(user.getLanguage()) ? SPANISH : ENGLISH;
    }
}
