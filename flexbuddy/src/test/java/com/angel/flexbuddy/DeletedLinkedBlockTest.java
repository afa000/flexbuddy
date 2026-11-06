package com.angel.flexbuddy;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
 * A block in Recently deleted can't be loaded through an expense's link, because Shift hides deleted rows. Every page
 * that lists or counts expenses must still load, and the expense counts by its date like an unlinked one until the
 * block is restored.
 */
@SpringBootTest(properties = {"flexbuddy.security.remember-me-key=deleted-link-key"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DeletedLinkedBlockTest {

    private static final String EMAIL = "deleted-link@example.com";

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository userRepository;
    @Autowired private PasswordEncoder encoder;
    @Autowired private JdbcTemplate jdbc;

    private long shiftId;
    private long expenseId;

    @BeforeEach
    void seed() throws Exception {
        wipe();
        userRepository.save(new AppUser("Link Tester", EMAIL, encoder.encode("link-password-1")));
        String shift = mockMvc.perform(post("/shifts").with(user(EMAIL)).with(csrf()).contentType("application/json")
                        .content("{\"station\":\"VEA7\",\"date\":\"2026-05-02\",\"startTime\":\"09:00\",\"endTime\":\"13:00\","
                                + "\"basePay\":80,\"tips\":0,\"miles\":10,\"status\":\"COMPLETED\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        shiftId = ((Number) JsonPath.read(shift, "$.id")).longValue();
        String expense = mockMvc.perform(post("/expenses").with(user(EMAIL)).with(csrf()).contentType("application/json")
                        .content("{\"date\":\"2026-05-03\",\"category\":\"TOLL\",\"amount\":4.5,\"note\":\"Bridge <b>north</b>\","
                                + "\"shiftId\":" + shiftId + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        expenseId = ((Number) JsonPath.read(expense, "$.id")).longValue();
        mockMvc.perform(delete("/shifts/{id}", shiftId).with(user(EMAIL)).with(csrf())).andExpect(status().isNoContent());
    }

    /** Native deletes, because the JPA repository can't see soft-deleted shifts and would leave them behind. */
    @AfterEach
    void wipe() {
        jdbc.update("delete from expense where owner_id in (select id from app_users where email = ?)", EMAIL);
        jdbc.update("delete from shift where owner_id in (select id from app_users where email = ?)", EMAIL);
        userRepository.findByEmailIgnoreCase(EMAIL).ifPresent(userRepository::delete);
    }

    @Test
    void everyPageThatReadsExpensesStillLoadsAndTheExpenseCountsByItsDate() throws Exception {
        mockMvc.perform(get("/expenses").with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].shiftDeleted").value(true))
                .andExpect(jsonPath("$[0].station").value("VEA7"));
        mockMvc.perform(get("/shifts/statistics").with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalExpenses").value(4.5));
        mockMvc.perform(get("/shifts/reports/earnings").param("groupBy", "station").with(user(EMAIL)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/expenses/export.csv").with(user(EMAIL))).andExpect(status().isOk());
        mockMvc.perform(get("/tax/summary").with(user(EMAIL))).andExpect(status().isOk());
    }

    @Test
    void recentlyDeletedExpensesLoadWithTheirNoteAndStation() throws Exception {
        mockMvc.perform(delete("/expenses/{id}", expenseId).with(user(EMAIL)).with(csrf()))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/expenses/trash").with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].note").value("Bridge <b>north</b>"))
                .andExpect(jsonPath("$[0].station").value("VEA7"))
                .andExpect(jsonPath("$[0].shiftDeleted").value(true));
    }

    @Test
    void editingTheExpenseWhileItsBlockIsDeletedKeepsTheLink() throws Exception {
        mockMvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put("/expenses/{id}", expenseId)
                        .with(user(EMAIL)).with(csrf()).contentType("application/json")
                        .content("{\"date\":\"2026-05-03\",\"category\":\"TOLL\",\"amount\":5,\"note\":\"Bridge\","
                                + "\"shiftId\":" + shiftId + "}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.shiftDeleted").value(true));
        mockMvc.perform(post("/shifts/{id}/restore", shiftId).with(user(EMAIL)).with(csrf())).andExpect(status().isOk());

        mockMvc.perform(get("/expenses").with(user(EMAIL)))
                .andExpect(jsonPath("$[0].shiftId").value(shiftId))
                .andExpect(jsonPath("$[0].shiftDeleted").value(false))
                .andExpect(jsonPath("$[0].amount").value(5));
    }

    @Test
    void restoringTheBlockLinksTheExpenseAgain() throws Exception {
        mockMvc.perform(post("/shifts/{id}/restore", shiftId).with(user(EMAIL)).with(csrf())).andExpect(status().isOk());

        mockMvc.perform(get("/expenses").with(user(EMAIL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].shiftDeleted").value(false))
                .andExpect(jsonPath("$[0].shiftId").value(shiftId));
    }
}
