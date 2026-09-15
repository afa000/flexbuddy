package com.angel.flexbuddy.config;

import java.time.Clock;

import javax.sql.DataSource;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.rememberme.InMemoryTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.JdbcTokenRepositoryImpl;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;

import com.angel.flexbuddy.repository.AppUserRepository;

@Configuration
@EnableWebSecurity
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
            PersistentTokenBasedRememberMeServices rememberMeServices) throws Exception {
        http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/login",
                                "/register",
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
                .formLogin(form -> form
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                        .permitAll()
                )
                .rememberMe(remember -> remember
                        .rememberMeServices(rememberMeServices)
                )
                .logout(logout -> logout
                        .logoutSuccessUrl("/login?logout")
                );

        return http.build();
    }
}
