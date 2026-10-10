package com.angel.flexbuddy.config;

import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.rememberme.InMemoryTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.security.AttemptLimiter;
import com.angel.flexbuddy.security.LockAwareFailureHandler;
import com.angel.flexbuddy.security.LoginAttemptFilter;
import com.angel.flexbuddy.security.SecurityLimitsProperties;
import com.angel.flexbuddy.security.VerifiedLoginSuccessHandler;

@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(SecurityLimitsProperties.class)
public class SecurityConfig {

    public static final String REMEMBER_ME_COOKIE = "FLEXBUDDY_REMEMBER_ME";
    public static final int REMEMBER_ME_VALIDITY_SECONDS = 30 * 24 * 60 * 60;

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    UserDetailsService userDetailsService(AppUserRepository userRepository) {
        return email -> userRepository.findByEmailIgnoreCase(email)
                .map(user -> User.withUsername(user.getEmail())
                        .password(user.getPasswordHash())
                        .roles("USER")
                        .build())
                .orElseThrow(() -> new org.springframework.security.core.userdetails.UsernameNotFoundException(
                        "Account not found."
                ));
    }

    @Bean
    PersistentTokenRepository persistentTokenRepository(ObjectProvider<DataSource> dataSourceProvider) {
        DataSource dataSource = dataSourceProvider.getIfAvailable();
        if (dataSource == null) {
            return new InMemoryTokenRepositoryImpl();
        }
        JdbcTokenRepositoryImpl repository = new JdbcTokenRepositoryImpl();
        repository.setDataSource(dataSource);
        return repository;
    }

    @Bean
    PersistentTokenBasedRememberMeServices rememberMeServices(
            @Value("${flexbuddy.security.remember-me-key}") String key,
            UserDetailsService userDetailsService,
            PersistentTokenRepository tokenRepository,
            Clock clock) {
        PersistentTokenBasedRememberMeServices services = new RotationTolerantRememberMeServices(
                key, userDetailsService, tokenRepository, clock);
        services.setParameter("remember-me");
        services.setCookieName(REMEMBER_ME_COOKIE);
        services.setTokenValiditySeconds(REMEMBER_ME_VALIDITY_SECONDS);
        services.setUseSecureCookie(true);
        services.setCookieCustomizer(cookie -> {
            cookie.setHttpOnly(true);
            cookie.setSecure(true);
            cookie.setAttribute("SameSite", "Lax");
        });
        return services;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
            PersistentTokenBasedRememberMeServices rememberMeServices,
            ObjectProvider<AttemptLimiter> limiterProvider,
            ObjectProvider<VerifiedLoginSuccessHandler> successHandlerProvider) throws Exception {
        // The limiter is always there in the running app; slice tests that do not load it simply sign in without limits.
        AttemptLimiter limiter = limiterProvider.getIfAvailable();
        // Slice tests that do not load the handler send everyone home, as before.
        VerifiedLoginSuccessHandler successHandler = successHandlerProvider.getIfAvailable();
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login",
                                "/register",
                                "/forgot-password",
                                "/reset-password",
                                "/verify-email",
                                "/verify-email/resend",
                                "/verify-email/cancel",
                                "/privacy",
                                "/terms",
                                "/delete-account",
                                "/error",
                                "/css/**",
                                "/js/**",
                                "/icons/**",
                                "/screenshots/**",
                                "/.well-known/**",
                                "/manifest.webmanifest",
                                "/sw.js",
                                "/offline.html",
                                "/calendar/*.ics",
                                "/actuator/health"
                        ).permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> {
                    form.loginPage("/login").permitAll();
                    if (successHandler != null) {
                        form.successHandler(successHandler);
                    } else {
                        form.defaultSuccessUrl("/", true);
                    }
                    if (limiter != null) {
                        form.failureHandler(new LockAwareFailureHandler(limiter));
                    }
                })
                // Shares arrive from another app without a token; the endpoint only redirects.
                .csrf(csrf -> csrf.ignoringRequestMatchers("/share-import"))
                .rememberMe(remember -> remember
                        .rememberMeServices(rememberMeServices)
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                );
        if (limiter != null) {
            http.addFilterBefore(new LoginAttemptFilter(limiter), UsernamePasswordAuthenticationFilter.class);
        }

        return http.build();
    }
}
