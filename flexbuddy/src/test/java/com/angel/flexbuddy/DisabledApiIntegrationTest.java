package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import com.angel.flexbuddy.api.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DisabledApiIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ApplicationContext context;
    @Autowired com.angel.flexbuddy.repository.AppUserRepository users;
    @Autowired com.angel.flexbuddy.repository.AppTokenRepository tokens;
    @Autowired com.angel.flexbuddy.repository.AppSignInChallengeRepository challenges;
    @Autowired com.angel.flexbuddy.repository.PasswordResetTokenRepository resets;
    @Autowired com.angel.flexbuddy.service.PasswordResetService passwordReset;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Test void passwordResetStillRevokesStoredCredentialsWhileDisabled() throws Exception {
        var now = java.time.Instant.now();
        var user = users.save(new com.angel.flexbuddy.model.AppUser("Disabled test", "disabled@example.com", "hash"));
        var token = new com.angel.flexbuddy.model.AppToken();
        token.setOwner(user);
        token.setTokenHash("a".repeat(64));
        token.setCreatedAt(now);
        token.setLastUsedAt(now);
        token.setExpiresAt(now.plusSeconds(3600));
        tokens.save(token);
        var challenge = new com.angel.flexbuddy.model.AppSignInChallenge();
        challenge.setOwner(user);
        challenge.setChallengeHash("b".repeat(64));
        challenge.setPurpose(com.angel.flexbuddy.model.AppSignInChallenge.Purpose.TWO_FACTOR);
        challenge.setCreatedAt(now);
        challenge.setExpiresAt(now.plusSeconds(600));
        challenges.save(challenge);
        var reset = new com.angel.flexbuddy.model.PasswordResetToken();
        reset.setOwner(user);
        reset.setCreatedAt(now);
        reset.setExpiresAt(now.plusSeconds(600));
        reset.setTokenHash(java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest("disabled-test-reset".getBytes(java.nio.charset.StandardCharsets.UTF_8))));
        resets.save(reset);
        jdbc.execute("create table if not exists persistent_logins (username varchar(254) not null, "
                + "series varchar(64) primary key, token varchar(64) not null, last_used timestamp not null)");
        try {
            passwordReset.resetPassword("disabled-test-reset", "new-test-password");
            assertThat(tokens.count()).isZero();
            assertThat(challenges.count()).isZero();
        } finally {
            tokens.deleteAll();
            challenges.deleteAll();
            resets.deleteAll();
            users.deleteAll();
        }
    }
    @Test void disabledByDefaultWithoutApiBeansAndEveryMethodReturns404() throws Exception {
        assertThat(context.getBeansOfType(ApiAuthController.class)).isEmpty();
        assertThat(context.getBeansOfType(ApiSignInService.class)).isEmpty();
        assertThat(context.getBeansOfType(AppTokenService.class)).isEmpty();
        assertThat(context.getBeansOfType(com.angel.flexbuddy.api.ApiShiftController.class)).isEmpty();
        assertThat(context.getBeansOfType(com.angel.flexbuddy.api.ApiShiftService.class)).isEmpty();
        assertThat(context.getBeansOfType(ApiShiftWriteController.class)).isEmpty();
        assertThat(context.getBeansOfType(ApiShiftWriteService.class)).isEmpty();
        assertThat(context.containsBean("apiSecurityFilterChain")).isFalse();
        for (String path : new String[]{"/api/v1", "/api/v1/account", "/api/v1/auth/sign-in", "/api/v1/unknown",
                "/api/v1/shifts", "/api/v1/shifts/1", "/api/v1/shifts/1/start", "/api/v1/shifts/1/status",
                "/api/v1/shifts/1/not-worked", "/api/v1/shifts/1/restore", "/api/v1/home"}) {
            mvc.perform(get(path)).andExpect(status().isNotFound()).andExpect(header().doesNotExist("Location"));
            mvc.perform(post(path)).andExpect(status().isNotFound());
            mvc.perform(delete(path)).andExpect(status().isNotFound());
            mvc.perform(put(path)).andExpect(status().isNotFound());
            mvc.perform(patch(path)).andExpect(status().isNotFound());
        }
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/login")).andExpect(status().isForbidden());
    }
}
