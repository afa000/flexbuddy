package com.angel.flexbuddy.testserver;

import java.util.EnumSet;

import jakarta.servlet.DispatcherType;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * The hosted test server only ({@code flexbuddy.test-server=true}, see docs/test-server.md). Production never sets the
 * flag, so none of these beans exist there. The banner and the header identify the copy; they are not access controls.
 */
@Configuration
@ConditionalOnProperty(name = TestServerConfig.PROPERTY, havingValue = "true")
public class TestServerConfig {

    public static final String PROPERTY = "flexbuddy.test-server";

    @Bean
    FilterRegistrationBean<NoIndexFilter> testServerNoIndexFilter() {
        FilterRegistrationBean<NoIndexFilter> bean = new FilterRegistrationBean<>(new NoIndexFilter());
        // After Spring's ForwardedHeaderFilter (HIGHEST_PRECEDENCE), before the client-address filter (+10) and
        // Spring Security (-100), so even a response the security chains end early carries the header.
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        // Error, forward and async dispatches too, in case anything resets the headers on the way to the error page.
        bean.setDispatcherTypes(EnumSet.allOf(DispatcherType.class));
        return bean;
    }
}
