package com.angel.flexbuddy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.oauth2.client.CommonOAuth2Provider;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;

/**
 * Google sign-in exists only when both the client ID and the secret are set in the environment. The registration is
 * built here, not in application.properties, because Spring Boot refuses to start when a registration's ID is empty.
 * The redirect address is Spring's default, {baseUrl}/login/oauth2/code/google.
 */
@Configuration
public class GoogleSignInConfig {

    @Bean
    @ConditionalOnExpression("!'${flexbuddy.google.client-id:}'.isBlank()")
    ClientRegistrationRepository clientRegistrationRepository(
            @Value("${flexbuddy.google.client-id}") String clientId,
            @Value("${flexbuddy.google.client-secret}") String clientSecret) {
        return new InMemoryClientRegistrationRepository(CommonOAuth2Provider.GOOGLE.getBuilder("google")
                .clientId(clientId)
                .clientSecret(clientSecret)
                .scope("openid", "email", "profile")
                .build());
    }
}
