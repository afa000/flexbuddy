package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.security.AttemptLimiter;

/**
 * The per-connection limits run through the real filter chain: a visitor who writes their own X-Forwarded-For gets no
 * new connection out of it, because the address used is the one the proxy added on the right.
 */
@SpringBootTest(properties = "flexbuddy.security.remember-me-key=client-address-test-key")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ClientAddressIntegrationTest {

    private static final String PROXY = "10.0.0.5";
    private static final String VISITOR = "198.51.100.7";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppUserRepository userRepository;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AttemptLimiter limiter;

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("drop table if exists persistent_logins");
        jdbcTemplate.execute("""
                create table persistent_logins (
                    username varchar(254) not null,
                    series varchar(64) primary key,
                    token varchar(64) not null,
                    last_used timestamp not null
                )
                """);
        shiftRepository.deleteAll();
        userRepository.deleteAll();
        limiter.clearAll();
    }

    @AfterEach
    void cleanUp() {
        jdbcTemplate.update("delete from persistent_logins");
        shiftRepository.deleteAll();
        userRepository.deleteAll();
        limiter.clearAll();
    }

    private MockHttpServletRequestBuilder register(String email, String forwardedFor) {
        return post("/register")
                .with(csrf())
                .with(request -> {
                    request.setRemoteAddr(PROXY);
                    return request;
                })
                .header("X-Forwarded-For", forwardedFor)
                .param("displayName", "Driver")
                .param("email", email)
                .param("password", "password123");
    }

    @Test
    void aVisitorWritingADifferentLeftEntryEachTimeIsStillOneConnection() throws Exception {
        for (int i = 1; i <= 10; i++) {
            mockMvc.perform(register("spoof" + i + "@example.com", "203.0.113." + i + ", " + VISITOR))
                    .andExpect(redirectedUrlPattern("**/verify-email"));
        }

        mockMvc.perform(register("spoof11@example.com", "203.0.113.11, " + VISITOR))
                .andExpect(status().isTooManyRequests());
        assertThat(userRepository.findByEmailIgnoreCase("spoof11@example.com")).isEmpty();
    }

    @Test
    void differentRealConnectionsDoNotShareTheLimit() throws Exception {
        for (int i = 1; i <= 11; i++) {
            mockMvc.perform(register("real" + i + "@example.com", "203.0.113.1, 198.51.100." + i))
                    .andExpect(redirectedUrlPattern("**/verify-email"));
        }
    }

    @Test
    void failedSignInsAreCountedAgainstTheAddressTheProxySaw() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(post("/login")
                    .secure(true)
                    .with(csrf())
                    .with(request -> {
                        request.setRemoteAddr(PROXY);
                        return request;
                    })
                    .header("X-Forwarded-For", "203.0.113.1, " + VISITOR)
                    .param("username", "guess" + i + "@example.com")
                    .param("password", "wrong-password"));
        }

        assertThat(limiter.isLocked(AttemptLimiter.LOGIN_IP, VISITOR)).isTrue();
        assertThat(limiter.isLocked(AttemptLimiter.LOGIN_IP, "203.0.113.1")).isFalse();
        assertThat(limiter.isLocked(AttemptLimiter.LOGIN_IP, PROXY)).isFalse();
    }

    @Test
    void calendarLinksStillUseTheSchemeAndHostTheProxyReported() throws Exception {
        AppUser owner = new AppUser("Link Test", "link@example.com", passwordEncoder.encode("password123"));
        owner.setCalendarToken("c".repeat(43));
        owner = userRepository.save(owner);
        shiftRepository.save(new Shift(null, "VEA7", LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(17, 0),
                new BigDecimal("100.00"), BigDecimal.ZERO, owner));

        mockMvc.perform(get("/calendar/{token}.ics", "c".repeat(43))
                        .with(request -> {
                            request.setRemoteAddr(PROXY);
                            return request;
                        })
                        .header("Host", "flexbuddy.onrender.com")
                        .header("X-Forwarded-Proto", "https")
                        .header("X-Forwarded-Host", "flexbuddy.onrender.com")
                        .header("X-Forwarded-For", "203.0.113.1, " + VISITOR))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("https://flexbuddy.onrender.com/?screen=schedule")));
    }
}
