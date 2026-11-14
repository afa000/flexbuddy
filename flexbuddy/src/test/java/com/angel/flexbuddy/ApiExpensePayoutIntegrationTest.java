package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.angel.flexbuddy.dto.ExpenseFilter;
import com.angel.flexbuddy.dto.ExpenseRequest;
import com.angel.flexbuddy.dto.PayoutDepositRequest;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Expense;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.TimestampListener;
import com.angel.flexbuddy.repository.AppTokenRepository;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;
import com.angel.flexbuddy.repository.PayoutDepositRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.security.MutableClock;
import com.angel.flexbuddy.service.ExpenseService;
import com.angel.flexbuddy.service.PayoutService;
import com.angel.flexbuddy.service.ShiftReportService;
import com.angel.flexbuddy.service.ShiftService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** NA-06: expenses and payouts over the API, with change-only expense sync and outbox writes. Made-up accounts only. */
@SpringBootTest(properties = {"flexbuddy.api.enabled=true", "flexbuddy.security.remember-me-key=api-test-key"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiExpensePayoutIntegrationTest {
    private static final String EMAIL = "expense-driver@example.test";
    private static final String OTHER = "expense-other@example.test";
    private static final String PASSWORD = "expense-test-password";
    /** Friday, November 13, 2026, 10:00 in New York: a default payout day (Tuesday and Friday). */
    private static final Instant START = Instant.parse("2026-11-13T15:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 11, 13);
    private static final ExpenseFilter ALL = new ExpenseFilter(null, null, null, null, null, null);

    @TestConfiguration
    static class Beans {
        @Bean @Primary MutableClock expenseClock() { return new MutableClock(START); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired AppTokenRepository tokens;
    @Autowired ShiftRepository shifts;
    @Autowired ExpenseRepository expenses;
    @Autowired PayoutDepositRepository deposits;
    @Autowired ShiftService shiftService;
    @Autowired ExpenseService expenseService;
    @Autowired PayoutService payoutService;
    @Autowired ShiftReportService reports;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private MutableClock testClock;
    private AppUser driver;
    private AppUser other;
    private String token;
    private String otherToken;

    @BeforeEach
    void before() throws Exception {
        wipe();
        // The listener's clock is static and shared by every cached test context; point it at this test's clock.
        testClock = clock;
        testClock.advance(Duration.between(testClock.instant(), START));
        new TimestampListener(testClock);
        driver = account("Expense Driver", EMAIL);
        other = account("Expense Other", OTHER);
        token = signIn(EMAIL);
        otherToken = signIn(OTHER);
    }

    @AfterEach
    void wipe() {
        tokens.deleteAll();
        deposits.deleteAll();
        jdbc.update("delete from api_write_receipt");
        // Rows in Recently deleted are hidden from entity queries, so remove every row directly.
        jdbc.update("delete from expense");
        jdbc.update("delete from shift");
        users.deleteAll();
    }

    @Test
    void everyEndpointNeedsAToken() throws Exception {
        for (String path : List.of("/api/v1/expenses", "/api/v1/expenses/1", "/api/v1/payouts")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
            mvc.perform(get(path).header("Authorization", "Bearer fbt_not-a-real-token"))
                    .andExpect(status().isUnauthorized());
            mvc.perform(get(path).with(user(EMAIL))).andExpect(status().isUnauthorized());
        }
        mvc.perform(post("/api/v1/expenses").header("Idempotency-Key", key()).contentType("application/json")
                .content(expense("12.50", null, null))).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/v1/payouts/2026-11-13").header("Idempotency-Key", key())
                .contentType("application/json").content(payout("100", null))).andExpect(status().isUnauthorized());
        assertThat(expenses.count()).isZero();
        assertThat(deposits.count()).isZero();
    }

    @Test
    void firstExpenseSyncReturnsOnlyThisAccountsLiveExpensesInTheApiConventions() throws Exception {
        Shift block = block(driver, "DXX1", ShiftStatus.COMPLETED);
        long linked = expenseService.create(EMAIL, request("12.5", block.getId(), "Fuel stop")).id();
        long removed = expenseService.create(EMAIL, request("4", null, null)).id();
        expenseService.delete(EMAIL, removed);
        expenseService.create(OTHER, request("99", null, null));

        JsonNode body = body(sync(token, null));
        assertThat(body.get("full").asBoolean()).isTrue();
        assertThat(ids(body.get("expenses"))).containsExactly(linked);
        assertThat(body.get("deleted")).isEmpty();
        JsonNode expense = body.get("expenses").get(0);
        assertThat(expense.get("date").asText()).isEqualTo("2026-11-12");
        assertThat(expense.get("category").asText()).isEqualTo("fuel");
        assertThat(expense.get("amount").isString()).isTrue();
        assertThat(expense.get("amount").asText()).isEqualTo("12.50");
        assertThat(expense.get("note").asText()).isEqualTo("Fuel stop");
        assertThat(expense.get("shiftId").asLong()).isEqualTo(block.getId());
        assertThat(expense.get("updatedAt").asText()).endsWith("Z");
        // Block-dependent facts stay out of the expense; the phone derives them from its block cache.
        assertThat(expense.has("counts")).isFalse();
        assertThat(expense.has("station")).isFalse();

        mvc.perform(get("/api/v1/expenses/" + linked).header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/expenses/" + removed).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/expenses").param("updatedSince", "not-a-cursor")
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
    }

    @Test
    void expenseSyncReportsEveryWriteDeleteAndRestorePathAndAnswers304WhenUnchanged() throws Exception {
        long websiteOne = expenseService.create(EMAIL, request("10", null, null)).id();
        long websiteTwo = expenseService.create(EMAIL, request("11", null, null)).id();
        testClock.advance(Duration.ofMinutes(10));
        MvcResult first = sync(token, null);
        String cursor = body(first).get("cursor").asText();
        String etag = first.getResponse().getHeader("ETag");
        mvc.perform(get("/api/v1/expenses").header("Authorization", "Bearer " + token).header("If-None-Match", etag))
                .andExpect(status().isNotModified());

        testClock.advance(Duration.ofMinutes(10));
        long created = body(write(token, post("/api/v1/expenses"), key(), expense("7.25", null, null), 201))
                .get("id").asLong();
        String deleteBatch = expenseService.delete(EMAIL, websiteOne);
        write(token, delete("/api/v1/expenses/" + websiteTwo).param("expectedUpdatedAt",
                updatedAt(websiteTwo).toString()), key(), null, 204);
        JsonNode changes = body(sync(token, cursor));
        assertThat(changes.get("full").asBoolean()).isFalse();
        assertThat(ids(changes.get("expenses"))).containsExactly(created);
        assertThat(ids(changes.get("deleted"))).containsExactlyInAnyOrder(websiteOne, websiteTwo);

        testClock.advance(Duration.ofMinutes(10));
        cursor = body(sync(token, cursor)).get("cursor").asText();
        testClock.advance(Duration.ofMinutes(10));
        expenseService.restoreBatch(EMAIL, deleteBatch);
        write(token, post("/api/v1/expenses/" + websiteTwo + "/restore"), key(), version(websiteTwo), 200);
        changes = body(sync(token, cursor));
        assertThat(changes.get("full").asBoolean()).isFalse();
        // The 5-minute overlap may send the earlier create again; upserting by id makes that harmless.
        assertThat(ids(changes.get("expenses"))).contains(websiteOne, websiteTwo);
        assertThat(changes.get("deleted")).isEmpty();
        // The block sync is not disturbed by expense changes.
        assertThat(body(mvc.perform(get("/api/v1/shifts").param("updatedSince", cursor)
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn())
                .get("full").asBoolean()).isFalse();
    }

    @Test
    void permanentRemovalsMakeTheNextExpenseSyncFull() throws Exception {
        long one = expenseService.create(EMAIL, request("10", null, null)).id();
        long two = expenseService.create(EMAIL, request("11", null, null)).id();
        long kept = expenseService.create(EMAIL, request("12", null, null)).id();
        expenseService.delete(EMAIL, one);
        expenseService.delete(EMAIL, two);
        testClock.advance(Duration.ofMinutes(10));
        String cursor = body(sync(token, null)).get("cursor").asText();

        testClock.advance(Duration.ofMinutes(10));
        expenseService.permanentlyDelete(EMAIL, one);
        JsonNode answer = body(sync(token, cursor));
        assertThat(answer.get("full").asBoolean()).isTrue();
        assertThat(ids(answer.get("expenses"))).containsExactly(kept);

        testClock.advance(Duration.ofMinutes(10));
        cursor = body(sync(token, null)).get("cursor").asText();
        testClock.advance(Duration.ofMinutes(10));
        assertThat(expenseService.emptyTrash(EMAIL)).isEqualTo(1);
        assertThat(body(sync(token, cursor)).get("full").asBoolean()).isTrue();

        // Answers stay full until a later change moves the cursor past the removal; then an empty Recently
        // deleted removes nothing and keeps change-only sync.
        testClock.advance(Duration.ofMinutes(10));
        expenseService.create(EMAIL, request("13", null, null));
        testClock.advance(Duration.ofMinutes(10));
        cursor = body(sync(token, null)).get("cursor").asText();
        testClock.advance(Duration.ofMinutes(10));
        assertThat(expenseService.emptyTrash(EMAIL)).isZero();
        assertThat(body(sync(token, cursor)).get("full").asBoolean()).isFalse();
    }

    @Test
    void apiExpenseWritesFollowTheWebsiteRulesAndRetriesNeverApplyTwice() throws Exception {
        Shift block = block(driver, "DXX1", ShiftStatus.COMPLETED);
        String createKey = key();
        String payload = expense("12.5", block.getId(), " Fuel ");
        MvcResult created = write(token, post("/api/v1/expenses"), createKey, payload, 201);
        long id = body(created).get("id").asLong();
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/expenses/" + id);
        // Same key and body: the same id, no second expense. Property order and spacing don't matter.
        String reordered = "{ \"shiftId\": " + block.getId() + ", \"note\": \" Fuel \", \"amount\": \"12.5\","
                + " \"category\": \"fuel\", \"date\": \"2026-11-12\" }";
        assertThat(body(write(token, post("/api/v1/expenses"), createKey, reordered, 201)).get("id").asLong())
                .isEqualTo(id);
        assertThat(expenseService.getExpenses(EMAIL, ALL)).hasSize(1);
        assertThat(read(id).get("note").asText()).isEqualTo("Fuel");
        assertThat(error(write(token, post("/api/v1/expenses"), createKey, expense("13", null, null), 409)))
                .isEqualTo("idempotency_conflict");

        String editKey = key();
        String edit = expense("15", null, null, updatedAt(id));
        write(token, put("/api/v1/expenses/" + id), editKey, edit, 200);
        assertThat(read(id).get("amount").asText()).isEqualTo("15.00");
        assertThat(read(id).get("shiftId").isNull()).isTrue();

        // A later website change: the delayed retry is acknowledged and does not overwrite it.
        testClock.advance(Duration.ofMinutes(1));
        expenseService.update(EMAIL, id, request("20", null, null));
        write(token, put("/api/v1/expenses/" + id), editKey, edit, 200);
        assertThat(read(id).get("amount").asText()).isEqualTo("20.00");
        // A new edit from the old version is a conflict carrying the current expense.
        JsonNode conflict = body(write(token, put("/api/v1/expenses/" + id), key(), expense("30", null, null,
                Instant.parse("2026-11-13T15:00:00Z")), 409));
        assertThat(conflict.get("error").asText()).isEqualTo("conflict");
        assertThat(conflict.get("current").get("amount").asText()).isEqualTo("20.00");

        String deleteKey = key();
        Instant beforeDelete = updatedAt(id);
        write(token, delete("/api/v1/expenses/" + id).param("expectedUpdatedAt", beforeDelete.toString()),
                deleteKey, null, 204);
        mvc.perform(get("/api/v1/expenses/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
        write(token, put("/api/v1/expenses/" + id), key(), expense("1", null, null, beforeDelete), 404);
        String restoreKey = key();
        String restore = "{\"expectedUpdatedAt\":\"" + beforeDelete + "\"}";
        write(token, post("/api/v1/expenses/" + id + "/restore"), restoreKey, restore, 200);
        assertThat(read(id).get("amount").asText()).isEqualTo("20.00");
        // Retrying the delete after the restore acknowledges it without deleting again.
        write(token, delete("/api/v1/expenses/" + id).param("expectedUpdatedAt", beforeDelete.toString()),
                deleteKey, null, 204);
        read(id);

        // After permanent deletion, retries neither recreate nor restore it.
        expenseService.delete(EMAIL, id);
        expenseService.permanentlyDelete(EMAIL, id);
        write(token, post("/api/v1/expenses/" + id + "/restore"), restoreKey, restore, 200);
        assertThat(body(write(token, post("/api/v1/expenses"), createKey, payload, 201)).get("id").asLong())
                .isEqualTo(id);
        assertThat(expenses.findAllIncludingDeleted(EMAIL)).isEmpty();
    }

    @Test
    void invalidWritesAreRefusedWithoutConsumingTheKey() throws Exception {
        String createKey = key();
        assertThat(body(write(token, post("/api/v1/expenses"), createKey, expense("-1", null, null), 400))
                .get("fields").has("amount")).isTrue();
        assertThat(body(write(token, post("/api/v1/expenses"), createKey,
                expense("1", null, null).replace("\"fuel\"", "\"snacks\""), 400)).get("fields").has("category"))
                .isTrue();
        write(token, post("/api/v1/expenses"), createKey, "{not json", 400);
        mvc.perform(post("/api/v1/expenses").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(expense("1", null, null))).andExpect(status().isBadRequest());
        write(token, post("/api/v1/expenses").header("Idempotency-Key", "not-a-uuid"), "also-not", expense("1", null, null), 400);
        assertThat(expenses.count()).isZero();
        long id = body(write(token, post("/api/v1/expenses"), createKey, expense("1", null, null), 201)).get("id").asLong();

        // Existing-record writes need the version the phone saw.
        write(token, put("/api/v1/expenses/" + id), key(), expense("2", null, null), 400);
        write(token, delete("/api/v1/expenses/" + id), key(), null, 400);
        assertThat(read(id).get("amount").asText()).isEqualTo("1.00");
    }

    @Test
    void anotherAccountsExpensesAndBlocksAnswer404AndAreNeverChanged() throws Exception {
        long theirs = expenseService.create(OTHER, request("50", null, null)).id();
        Shift theirBlock = block(other, "DXX9", ShiftStatus.COMPLETED);
        Instant version = expenses.findById(theirs).orElseThrow().getUpdatedAt();
        write(token, put("/api/v1/expenses/" + theirs), key(), expense("1", null, null, version), 404);
        write(token, delete("/api/v1/expenses/" + theirs).param("expectedUpdatedAt", version.toString()), key(), null, 404);
        write(token, post("/api/v1/expenses/" + theirs + "/restore"), key(), "{\"expectedUpdatedAt\":\"" + version + "\"}", 404);
        JsonNode linkRefused = body(write(token, post("/api/v1/expenses"), key(),
                expense("5", theirBlock.getId(), null), 404));
        assertThat(linkRefused.get("field").asText()).isEqualTo("shiftId");
        assertThat(expenses.findById(theirs).orElseThrow().getAmount()).isEqualByComparingTo("50");
        assertThat(expenseService.getExpenses(EMAIL, ALL)).isEmpty();

        // The same key in another account is independent.
        String shared = key();
        write(token, post("/api/v1/expenses"), shared, expense("5", null, null), 201);
        write(otherToken, post("/api/v1/expenses"), shared, expense("6", null, null), 201);
        assertThat(expenseService.getExpenses(OTHER, ALL)).hasSize(2);
    }

    @Test
    void keysAreSharedAcrossBlockExpenseAndPayoutWrites() throws Exception {
        String shared = key();
        write(token, post("/api/v1/shifts"), shared, "{\"station\":\"DXX1\",\"date\":\"2026-11-13\","
                + "\"startTime\":\"10:30\",\"endTime\":\"13:30\",\"basePay\":\"72\",\"tips\":\"0\","
                + "\"status\":\"scheduled\"}", 201);
        assertThat(error(write(token, post("/api/v1/expenses"), shared, expense("5", null, null), 409)))
                .isEqualTo("idempotency_conflict");
        assertThat(error(write(token, put("/api/v1/payouts/2026-11-13"), shared, payout("5", null), 409)))
                .isEqualTo("idempotency_conflict");
        assertThat(expenses.count()).isZero();
        assertThat(deposits.count()).isZero();
    }

    @Test
    void restoreIsRefusedAtThirtyDaysEvenBeforeThePurge() throws Exception {
        long id = expenseService.create(EMAIL, request("10", null, null)).id();
        Instant version = updatedAt(id);
        expenseService.delete(EMAIL, id);
        testClock.advance(Duration.ofDays(30));
        token = signIn(EMAIL);
        write(token, post("/api/v1/expenses/" + id + "/restore"), key(), "{\"expectedUpdatedAt\":\"" + version + "\"}", 404);
        assertThat(expenses.findAllIncludingDeleted(EMAIL).getFirst().getDeletedAt()).isNotNull();
    }

    @Test
    void parallelCreatesWithOneKeyAddOneExpense() throws Exception {
        String createKey = key();
        String payload = expense("12", null, null);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Long> action = () -> {
                gate.await();
                return body(write(token, post("/api/v1/expenses"), createKey, payload, 201)).get("id").asLong();
            };
            var a = executor.submit(action);
            var b = executor.submit(action);
            gate.countDown();
            assertThat(a.get(20, java.util.concurrent.TimeUnit.SECONDS))
                    .isEqualTo(b.get(20, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(expenses.count()).isEqualTo(1);
    }

    @Test
    void expensesOnScheduledBlocksStillWaitAndDeletedBlocksCountByDate() throws Exception {
        Shift scheduled = block(driver, "DXX2", ShiftStatus.SCHEDULED);
        Shift deleted = block(driver, "DXX3", ShiftStatus.COMPLETED);
        long waiting = body(write(token, post("/api/v1/expenses"), key(), expense("8", scheduled.getId(), null), 201))
                .get("id").asLong();
        long counted = body(write(token, post("/api/v1/expenses"), key(), expense("9", deleted.getId(), null), 201))
                .get("id").asLong();
        shiftService.deleteShift(EMAIL, deleted.getId());
        List<Long> countable = expenseService.countable(EMAIL, expenses.findAllIncludingDeleted(EMAIL)).stream()
                .map(Expense::getId).toList();
        assertThat(countable).containsExactly(counted);
        assertThat(countable).doesNotContain(waiting);
        // An edit that keeps the link to the deleted block is accepted, as on the website.
        write(token, put("/api/v1/expenses/" + counted), key(), expense("10", deleted.getId(), null, updatedAt(counted)), 200);
        assertThat(read(counted).get("shiftId").asLong()).isEqualTo(deleted.getId());
    }

    @Test
    void payoutListMatchesTheWebsiteAndAnswers304WhenUnchanged() throws Exception {
        block(driver, "DXX1", ShiftStatus.COMPLETED);
        payoutService.record(EMAIL, TODAY, new PayoutDepositRequest(new BigDecimal("50"), "Bank"));
        MvcResult result = mvc.perform(get("/api/v1/payouts").param("count", "3")
                .header("Authorization", "Bearer " + token)).andExpect(status().isOk()).andReturn();
        JsonNode body = body(result);
        var website = reports.payPeriods(EMAIL, 3);
        assertThat(body.get("periods")).hasSize(website.periods().size());
        for (int index = 0; index < website.periods().size(); index++) {
            var expected = website.periods().get(index);
            JsonNode period = body.get("periods").get(index);
            assertThat(period.get("payoutDate").asText()).isEqualTo(expected.payoutDate().toString());
            assertThat(period.get("from").asText()).isEqualTo(expected.from().toString());
            assertThat(period.get("to").asText()).isEqualTo(expected.to().toString());
            assertThat(period.get("blocks").asInt()).isEqualTo(expected.blocks());
            assertThat(new BigDecimal(period.get("earned").asText())).isEqualByComparingTo(expected.earned());
            assertThat(period.get("status").asText()).isEqualTo(expected.status().name().toLowerCase());
        }
        JsonNode next = body.get("nextPayout");
        assertThat(next.get("payoutDate").asText()).isEqualTo(website.nextPayout().payoutDate().toString());
        JsonNode recorded = find(body, TODAY);
        assertThat(recorded.get("received").asText()).isEqualTo("50.00");
        assertThat(recorded.get("note").asText()).isEqualTo("Bank");
        assertThat(recorded.get("updatedAt").asText()).isEqualTo(deposit(TODAY).toString());

        mvc.perform(get("/api/v1/payouts").param("count", "3").header("Authorization", "Bearer " + token)
                .header("If-None-Match", result.getResponse().getHeader("ETag"))).andExpect(status().isNotModified());
        mvc.perform(get("/api/v1/payouts").param("count", "0").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
        // Another account sees only its own payouts.
        assertThat(find(body(mvc.perform(get("/api/v1/payouts").param("count", "3")
                .header("Authorization", "Bearer " + otherToken)).andReturn()), TODAY).get("received").isNull()).isTrue();
    }

    @Test
    void payoutWritesUseTheWebsiteRulesVersionsAndRetries() throws Exception {
        String recordKey = key();
        String first = payout("100", null);
        write(token, put("/api/v1/payouts/2026-11-13"), recordKey, first, 200);
        assertThat(deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(EMAIL, TODAY).orElseThrow().getAmount())
                .isEqualByComparingTo("100");
        // Recording again from "nothing recorded" is now stale.
        JsonNode stale = body(write(token, put("/api/v1/payouts/2026-11-13"), key(), payout("90", null), 409));
        assertThat(stale.get("current").get("amount").asText()).isEqualTo("100.00");

        testClock.advance(Duration.ofMinutes(1));
        String editKey = key();
        String edit = payout("110", deposit(TODAY));
        write(token, put("/api/v1/payouts/2026-11-13"), editKey, edit, 200);
        // A website change after the edit: delayed retries of either write are acknowledged without applying again.
        testClock.advance(Duration.ofMinutes(1));
        payoutService.record(EMAIL, TODAY, new PayoutDepositRequest(new BigDecimal("120"), null));
        write(token, put("/api/v1/payouts/2026-11-13"), editKey, edit, 200);
        write(token, put("/api/v1/payouts/2026-11-13"), recordKey, first, 200);
        assertThat(deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(EMAIL, TODAY).orElseThrow().getAmount())
                .isEqualByComparingTo("120");

        String removeKey = key();
        write(token, delete("/api/v1/payouts/2026-11-13").param("expectedUpdatedAt", "2026-11-13T15:00:00Z"),
                removeKey, null, 409);
        assertThat(deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(EMAIL, TODAY)).isPresent();
        Instant current = deposit(TODAY);
        write(token, delete("/api/v1/payouts/2026-11-13").param("expectedUpdatedAt", current.toString()),
                removeKey, null, 204);
        assertThat(deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(EMAIL, TODAY)).isEmpty();
        // A retried record after the removal never brings it back.
        write(token, put("/api/v1/payouts/2026-11-13"), editKey, edit, 200);
        write(token, delete("/api/v1/payouts/2026-11-13").param("expectedUpdatedAt", current.toString()),
                removeKey, null, 204);
        assertThat(deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(EMAIL, TODAY)).isEmpty();

        // The website's rules, as reason codes; a refused write doesn't use up its key.
        String refusedKey = key();
        assertThat(body(write(token, put("/api/v1/payouts/2026-11-17"), refusedKey, payout("5", null), 400))
                .get("reason").asText()).isEqualTo("error.payout.notYet");
        assertThat(body(write(token, put("/api/v1/payouts/2026-11-12"), key(), payout("5", null), 400))
                .get("reason").asText()).isEqualTo("error.payout.notPayoutDay");
        assertThat(body(write(token, put("/api/v1/payouts/2026-11-10"), key(), payout("-5", null), 400))
                .get("fields").has("amount")).isTrue();
        write(token, put("/api/v1/payouts/2026-11-10"), refusedKey, payout("0", null), 200);
        assertThat(deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(EMAIL, LocalDate.of(2026, 11, 10))
                .orElseThrow().getAmount()).isEqualByComparingTo("0");
        write(token, put("/api/v1/payouts/not-a-date"), key(), payout("5", null), 400);

        // Another account's payout on the same date is untouched and independent.
        mvc.perform(put("/api/v1/payouts/2026-11-13").header("Authorization", "Bearer " + otherToken)
                .header("Idempotency-Key", recordKey).contentType("application/json").content(first))
                .andExpect(status().isOk());
        assertThat(deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(OTHER, TODAY)).isPresent();
        assertThat(deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(EMAIL, TODAY)).isEmpty();
    }

    // Helpers

    private AppUser account(String name, String email) {
        AppUser user = new AppUser(name, email, encoder.encode(PASSWORD));
        user.setTimeZone("America/New_York");
        return users.save(user);
    }

    private Shift block(AppUser owner, String station, ShiftStatus status) {
        LocalDate date = status == ShiftStatus.SCHEDULED ? TODAY.plusDays(1) : TODAY.minusDays(1);
        Shift shift = new Shift(null, station, date, LocalTime.of(10, 30), LocalTime.of(13, 30),
                new BigDecimal("72"), BigDecimal.ZERO, owner);
        shift.setStatus(status);
        return shifts.saveAndFlush(shift);
    }

    private static ExpenseRequest request(String amount, Long shiftId, String note) {
        return new ExpenseRequest(LocalDate.of(2026, 11, 12), ExpenseCategory.FUEL, new BigDecimal(amount), note, shiftId);
    }

    private static String expense(String amount, Long shiftId, String note) {
        return expense(amount, shiftId, note, null);
    }

    private static String expense(String amount, Long shiftId, String note, Instant expected) {
        return "{\"date\":\"2026-11-12\",\"category\":\"fuel\",\"amount\":\"" + amount + "\""
                + (note == null ? "" : ",\"note\":\"" + note + "\"")
                + (shiftId == null ? "" : ",\"shiftId\":" + shiftId)
                + (expected == null ? "" : ",\"expectedUpdatedAt\":\"" + expected + "\"") + "}";
    }

    private static String payout(String amount, Instant expected) {
        return "{\"amount\":\"" + amount + "\"" + (expected == null ? "" : ",\"expectedUpdatedAt\":\"" + expected + "\"") + "}";
    }

    private String version(long id) { return "{\"expectedUpdatedAt\":\"" + updatedAt(id) + "\"}"; }

    private Instant updatedAt(long id) {
        return expenses.findAllIncludingDeleted(EMAIL).stream().filter(expense -> expense.getId() == id)
                .findFirst().orElseThrow().getUpdatedAt();
    }

    private Instant deposit(LocalDate date) {
        return deposits.findByOwnerEmailIgnoreCaseAndPayoutDate(EMAIL, date).orElseThrow().getUpdatedAt();
    }

    private static JsonNode find(JsonNode payouts, LocalDate date) {
        for (JsonNode period : payouts.get("periods")) {
            if (period.get("payoutDate").asText().equals(date.toString())) return period;
        }
        JsonNode next = payouts.get("nextPayout");
        if (next != null && next.get("payoutDate").asText().equals(date.toString())) return next;
        throw new AssertionError("No pay period for " + date);
    }

    private JsonNode read(long id) throws Exception {
        return body(mvc.perform(get("/api/v1/expenses/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
    }

    private MvcResult write(String bearer, MockHttpServletRequestBuilder request, String key, String payload,
            int expectedStatus) throws Exception {
        request.header("Authorization", "Bearer " + bearer).header("Idempotency-Key", key);
        if (payload != null) request.contentType("application/json").content(payload);
        return mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
    }

    private MvcResult sync(String bearer, String cursor) throws Exception {
        var request = get("/api/v1/expenses").header("Authorization", "Bearer " + bearer);
        if (cursor != null) request = request.param("updatedSince", cursor);
        return mvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private String signIn(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/sign-in").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return body(result).get("token").asText();
    }

    private String error(MvcResult result) throws Exception { return body(result).get("error").asText(); }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static String key() { return UUID.randomUUID().toString(); }

    private static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode node : array) ids.add(node.isObject() ? node.get("id").asLong() : node.asLong());
        return ids;
    }
}
