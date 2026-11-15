package com.angel.flexbuddy.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** NA-07: registers {@link ApiUploadLimitFilter} only while the API is turned on. */
@Configuration
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiUploadLimitConfig {
    @Bean
    FilterRegistrationBean<ApiUploadLimitFilter> apiUploadLimitFilter() {
        FilterRegistrationBean<ApiUploadLimitFilter> bean = new FilterRegistrationBean<>(new ApiUploadLimitFilter());
        bean.addUrlPatterns("/api/v1/*");
        // After the client-address filter (HIGHEST_PRECEDENCE + 10) and before the HiddenHttpMethodFilter (-10000),
        // which is the first to read parameters.
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return bean;
    }
}
