package com.angel.flexbuddy.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

import com.angel.flexbuddy.security.ClientAddressFilter;
import com.angel.flexbuddy.security.ClientAddressProperties;

/** Registers the filter that decides which address the per-connection limits use. */
@Configuration
@EnableConfigurationProperties(ClientAddressProperties.class)
public class ClientAddressConfig {

    @Bean
    FilterRegistrationBean<ClientAddressFilter> clientAddressFilter(ClientAddressProperties properties, Clock clock) {
        FilterRegistrationBean<ClientAddressFilter> bean = new FilterRegistrationBean<>(new ClientAddressFilter(properties, clock));
        // After Spring's ForwardedHeaderFilter (scheme and host, at HIGHEST_PRECEDENCE) and before Spring Security (-100).
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return bean;
    }
}
