package com.angel.flexbuddy.config;

import java.time.Duration;
import java.util.Locale;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.SimpleLocaleContext;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.i18n.CookieLocaleResolver;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

import com.angel.flexbuddy.i18n.UserLocales;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Which language a request is served in: English or Spanish. A saved choice (the {@code fb_lang} cookie, set at sign-in,
 * from Account, or by {@code ?lang=es} on any page) wins, then the browser's own preference, then English.
 */
@Configuration
public class LocaleConfig implements WebMvcConfigurer {

    static final String COOKIE = UserLocales.COOKIE;
    static final String PARAMETER = "lang";

    @Bean
    LocaleResolver localeResolver() {
        return new FlexBuddyLocaleResolver();
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName(PARAMETER);
        interceptor.setIgnoreInvalidLocale(true);
        registry.addInterceptor(interceptor);
    }

    /** A cookie that remembers the choice for a year; whatever it holds, the language is always one we speak. */
    public static class FlexBuddyLocaleResolver extends CookieLocaleResolver {

        public FlexBuddyLocaleResolver() {
            super(COOKIE);
            setCookieMaxAge(Duration.ofDays(365));
            setCookieSameSite("Lax");
            setCookieHttpOnly(true);
            setDefaultLocaleFunction(request -> bestMatch(request));
        }

        /** The first language in the browser's preference list that we speak; English when it names none. */
        static Locale bestMatch(HttpServletRequest request) {
            return UserLocales.browserPreference(request);
        }

        @Override
        public Locale resolveLocale(HttpServletRequest request) {
            return UserLocales.clamp(super.resolveLocale(request));
        }

        @Override
        public LocaleContext resolveLocaleContext(HttpServletRequest request) {
            return new SimpleLocaleContext(UserLocales.clamp(super.resolveLocaleContext(request).getLocale()));
        }

        @Override
        public void setLocale(HttpServletRequest request, HttpServletResponse response, Locale locale) {
            if (locale == null) {
                request.setAttribute(LOCALE_REQUEST_ATTRIBUTE_NAME, bestMatch(request));
                response.addHeader(HttpHeaders.SET_COOKIE, ResponseCookie.from(COOKIE, "")
                        .path("/").maxAge(Duration.ZERO).httpOnly(true).sameSite("Lax")
                        .secure(request.isSecure()).build().toString());
            } else {
                super.setLocale(request, response, UserLocales.clamp(locale));
            }
        }
    }
}
