package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.jayway.jsonpath.JsonPath;

/**
 * Importing several screenshots (FB-021) gives every screenshot its own request id, and a retried save is sent with
 * the same id. These are the server answers the batch relies on: a repeat is answered with the first block and adds
 * nothing, even with different values, and an id is only ever looked up among the driver's own blocks.
 */
@SpringBootTest(properties = {"flexbuddy.security.remember-me-key=batch-import-key"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BatchImportSaveTest {

    private static final String EMAIL = "batch-import@example.com";
    private static final String OTHER = "batch-import-other@example.com";
    private static final String KEY = "0b5d2f3e-7a41-4c3a-9e0f-5b6c7d8e9f01";
    private static final String STATUS_KEY = "0b5d2f3e-7a41-4c3a-9e0f-5b6c7d8e9f02";
    private static final String COMPLETED = "{\"station\":\"VEA7\",\"date\":\"2026-05-02\",\"startTime\":\"09:00\","
            + "\"endTime\":\"13:00\",\"basePay\":%s,\"tips\":0,\"miles\":null,\"status\":\"COMPLETED\"}";
    private static final String SCHEDULED = "{\"station\":\"VEA7\",\"date\":\"2026-05-09\",\"startTime\":\"09:00\","
            + "\"endTime\":\"13:00\",\"basePay\":80,\"tips\":0,\"miles\":null,\"status\":\"SCHEDULED\"}";

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void seed() {
        wipe();
        userRepository.save(new AppUser("Batch Tester", EMAIL, encoder.encode("batch-password-1")));
        userRepository.save(new AppUser("Other Driver", OTHER, encoder.encode("batch-password-2")));
    }

    @AfterEach
    void wipe() {
        for (String email : new String[] {EMAIL, OTHER}) {
            jdbc.update("delete from shift where owner_id in (select id from app_users where email = ?)", email);
            userRepository.findByEmailIgnoreCase(email).ifPresent(userRepository::delete);
        }
    }

    @Test
    void aRetriedSaveIsAnsweredWithTheFirstBlockEvenAfterAnEdit() throws Exception {
        long first = create(EMAIL, KEY, COMPLETED.formatted("80"));

        // The first answer was lost and the driver changed the pay before trying again.
        String retry = mockMvc.perform(post("/shifts").with(user(EMAIL)).with(csrf()).header("Idempotency-Key", KEY)
                        .contentType("application/json").content(COMPLETED.formatted("95")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.basePay").value(80))
                .andReturn().getResponse().getContentAsString();

        assertThat(((Number) JsonPath.read(retry, "$.id")).longValue()).isEqualTo(first);
        assertThat(blocks(EMAIL)).isEqualTo(1);
        mockMvc.perform(get("/shifts/statistics").with(user(EMAIL)))
                .andExpect(jsonPath("$.totalShifts").value(1))
                .andExpect(jsonPath("$.totalEarnings").value(80.0));
    }

    @Test
    void anotherDriversRequestIdNeverAnswersWithTheirBlock() throws Exception {
        long mine = create(EMAIL, KEY, COMPLETED.formatted("80"));
        long theirs = create(OTHER, KEY, COMPLETED.formatted("60"));

        assertThat(theirs).isNotEqualTo(mine);
        assertThat(blocks(EMAIL)).isEqualTo(1);
        assertThat(blocks(OTHER)).isEqualTo(1);
    }

    @Test
    void aScheduledBlockFromOneScreenshotIsCompletedOnceByAnother() throws Exception {
        long scheduled = create(EMAIL, KEY, SCHEDULED);
        mockMvc.perform(get("/shifts/statistics").with(user(EMAIL)))
                .andExpect(jsonPath("$.totalEarnings").value(0));

        String complete = "{\"status\":\"COMPLETED\",\"basePay\":92,\"tips\":8,\"miles\":null}";
        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(patch("/shifts/{id}/status", scheduled).with(user(EMAIL)).with(csrf())
                            .header("Idempotency-Key", STATUS_KEY).contentType("application/json").content(complete))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.basePay").value(92));
        }

        assertThat(blocks(EMAIL)).isEqualTo(1);
        mockMvc.perform(get("/shifts/statistics").with(user(EMAIL)))
                .andExpect(jsonPath("$.totalEarnings").value(100.0));
    }

    private long create(String email, String key, String body) throws Exception {
        String created = mockMvc.perform(post("/shifts").with(user(email)).with(csrf()).header("Idempotency-Key", key)
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(created, "$.id")).longValue();
    }

    private int blocks(String email) {
        return jdbc.queryForObject(
                "select count(*) from shift where owner_id in (select id from app_users where email = ?)",
                Integer.class, email);
    }
}
