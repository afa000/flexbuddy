package com.angel.flexbuddy.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.DispatcherServlet;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;

class FlexBuddyLocaleResolverTest {
    private final LocaleConfig.FlexBuddyLocaleResolver resolver = new LocaleConfig.FlexBuddyLocaleResolver();

    @ParameterizedTest
    @CsvSource({"'es-MX,es;q=0.9',es", "'fr-FR,es;q=0.5',es", "de-DE,en", "'en,es;q=0.5',en"})
    void followsFirstSupportedBrowserLanguage(String header, String expected) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", header);
        assertThat(resolver.resolveLocale(request).getLanguage()).isEqualTo(expected);
    }

    @Test
    void defaultsToEnglishAndCookieWinsOverBrowser() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThat(resolver.resolveLocale(request)).isEqualTo(Locale.ENGLISH);
        request = new MockHttpServletRequest();
        request.addHeader("Accept-Language", "en");
        request.setCookies(new Cookie("fb_lang", "es"));
        assertThat(resolver.resolveLocaleContext(request).getLocale().getLanguage()).isEqualTo("es");
    }

    @ParameterizedTest
    @CsvSource({"es,es", "xx,en"})
    void languageParameterSetsASupportedCookie(String requested, String expected) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(DispatcherServlet.LOCALE_RESOLVER_ATTRIBUTE, resolver);
        request.setParameter("lang", requested);
        MockHttpServletResponse response = new MockHttpServletResponse();
        LocaleChangeInterceptor interceptor = new LocaleChangeInterceptor();
        interceptor.setParamName("lang");
        interceptor.preHandle(request, response, new Object());
        assertThat(resolver.resolveLocale(request).getLanguage()).isEqualTo(expected);
        assertThat(response.getCookie("fb_lang").getValue()).isEqualTo(expected);
    }
}
