package com.angel.flexbuddy.api;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.savedrequest.NullRequestCache;

@Configuration
@ConditionalOnProperty(name = "flexbuddy.api.enabled", havingValue = "true")
public class ApiSecurityConfig {
    @Bean @Order(1)
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http, AppTokenService tokens,
            UserDetailsService users) throws Exception {
        return http.securityMatcher("/api/v1/**")
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(c -> c.securityContextRepository(new NullSecurityContextRepository()))
                .requestCache(c -> c.requestCache(new NullRequestCache()))
                .csrf(c -> c.disable()).formLogin(c -> c.disable()).httpBasic(c -> c.disable())
                .rememberMe(c -> c.disable()).logout(c -> c.disable())
                .authorizeHttpRequests(a -> a.requestMatchers(HttpMethod.POST, "/api/v1/auth/sign-in",
                        "/api/v1/auth/code", "/api/v1/auth/code/resend").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, failure) -> {
                    response.setStatus(401);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"unauthorized\"}");
                }).accessDeniedHandler((request, response, failure) -> {
                    response.setStatus(403);
                    response.setContentType("application/json");
                    response.getWriter().write("{\"error\":\"forbidden\"}");
                }))
                // Deliberately not a servlet-filter bean: it must run on this chain only.
                .addFilterBefore(new ApiTokenFilter(tokens, users), UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
