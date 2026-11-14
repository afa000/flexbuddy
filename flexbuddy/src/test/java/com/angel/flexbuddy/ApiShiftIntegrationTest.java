package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import com.angel.flexbuddy.dto.ShiftFilter;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.TimestampListener;
import com.angel.flexbuddy.repository.AppTokenRepository;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.security.MutableClock;
import com.angel.flexbuddy.service.ShiftReportService;
import com.angel.flexbuddy.service.ShiftService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** FB-033: read-only blocks and Home over the API, with change-only sync. Made-up accounts only. */
@SpringBootTest(properties = {"flexbuddy.api.enabled=true", "flexbuddy.security.remember-me-key=api-test-key"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiShiftIntegrationTest {
    private static final String EMAIL = "sync-driver@example.test";
    private static final String OTHER = "sync-other@example.test";
    private static final String PASSWORD = "sync-test-password";
    /** Friday, November 13, 2026, 10:00 in New York. */
    private static final Instant START = Instant.parse("2026-11-13T15:00:00Z");

    @TestConfiguration
    static class Beans {
        @Bean @Primary MutableClock syncClock() { return new MutableClock(START); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired AppTokenRepository tokens;
    @Autowired ShiftRepository shifts;
    @Autowired ExpenseRepository expenses;
    @Autowired ShiftService shiftService;
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
        driver = account("Sync Driver", EMAIL);
        other = account("Sync Other", OTHER);
        token = signIn(EMAIL);
        otherToken = signIn(OTHER);
    }

    @AfterEach
    void wipe() {
        tokens.deleteAll();
        expenses.deleteAll();
        // Blocks in Recently deleted are hidden from entity queries, so remove every row directly.
        jdbc.update("delete from shift");
        users.deleteAll();
    }

    @Test
    void everyEndpointNeedsAToken() throws Exception {
        for (String path : List.of("/api/v1/shifts", "/api/v1/shifts/1", "/api/v1/home")) {
            mvc.perform(get(path)).andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("Location"));
            mvc.perform(get(path).header("Authorization", "Bearer fbt_not-a-real-token"))
                    .andExpect(status().isUnauthorized());
            // A browser session never authenticates the API.
            mvc.perform(get(path).with(user(EMAIL))).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void firstSyncReturnsEveryLiveBlockOfThisAccountInTheApiConventions() throws Exception {
        Shift block = save(driver, "DXX1", LocalDate.of(2026, 11, 10), "72", "18.5");
        block.setMiles(new BigDecimal("41.5"));
        shifts.saveAndFlush(block);
        Shift deleted = save(driver, "DXX2", LocalDate.of(2026, 11, 11), "80", "0");
        shiftService.deleteShift(EMAIL, deleted.getId());
        save(other, "DXX9", LocalDate.of(2026, 11, 10), "99", "0");

        JsonNode body = body(sync(token, null));
        assertThat(body.get("full").asBoolean()).isTrue();
        assertThat(ids(body.get("shifts"))).containsExactly(block.getId());
        assertThat(body.get("deleted")).isEmpty();
        assertThat(body.get("cursor").asText()).isNotBlank();
        JsonNode shift = body.get("shifts").get(0);
        assertThat(shift.get("station").asText()).isEqualTo("DXX1");
        assertThat(shift.get("date").asText()).isEqualTo("2026-11-10");
        assertThat(shift.get("startTime").asText()).isEqualTo("18:30");
        assertThat(shift.get("endTime").asText()).isEqualTo("21:30");
        assertThat(shift.get("status").asText()).isEqualTo("completed");
        assertThat(shift.get("basePay").isString()).isTrue();
        assertThat(shift.get("basePay").asText()).isEqualTo("72.00");
        assertThat(shift.get("tips").asText()).isEqualTo("18.50");
        assertThat(shift.get("totalPay").asText()).isEqualTo("90.50");
        assertThat(shift.get("miles").asText()).isEqualTo("41.5");
        assertThat(shift.get("createdAt").asText()).isEqualTo(START.toString());
        // Figures that depend on expenses or settings stay out of a block (see ApiShift).
        assertThat(shift.has("netPay")).isFalse();
        assertThat(shift.has("mileageCost")).isFalse();
        assertThat(shift.has("forfeitDeadline")).isFalse();
    }

    @Test
    void laterSyncsReturnOnlyChangedBlocksAndTheIdsOfDeletedOnes() throws Exception {
        Shift untouched = save(driver, "DXX1", LocalDate.of(2026, 11, 10), "71", "0");
        // Changes within the overlap before the cursor are sent again, so the untouched block is older than that.
        testClock.advance(Duration.ofMinutes(10));
        Shift edited = save(driver, "DXX1", LocalDate.of(2026, 11, 9), "70", "0");
        Shift removed = save(driver, "DXX1", LocalDate.of(2026, 11, 11), "72", "0");
        String cursor = body(sync(token, null)).get("cursor").asText();

        testClock.advance(Duration.ofMinutes(10));
        edited.setTips(new BigDecimal("5.00"));
        shifts.saveAndFlush(edited);
        Shift added = save(driver, "DXX2", LocalDate.of(2026, 11, 12), "73", "0");
        Instant updatedBefore = updatedAt(removed.getId());
        shiftService.deleteShift(EMAIL, removed.getId());
        save(other, "DXX9", LocalDate.of(2026, 11, 12), "99", "0");

        JsonNode delta = body(sync(token, cursor));
        assertThat(delta.get("full").asBoolean()).isFalse();
        assertThat(ids(delta.get("shifts"))).containsExactly(edited.getId(), added.getId());
        assertThat(ids(delta.get("deleted"))).containsExactly(removed.getId());
        assertThat(ids(delta.get("shifts"))).doesNotContain(untouched.getId());
        // Deleting doesn't make the block look edited on the website.
        assertThat(updatedAt(removed.getId())).isEqualTo(updatedBefore);

        testClock.advance(Duration.ofMinutes(10));
        String next = delta.get("cursor").asText();
        shiftService.restoreShift(EMAIL, removed.getId());
        JsonNode restored = body(sync(token, next));
        assertThat(restored.get("full").asBoolean()).isFalse();
        // The blocks at the previous cursor come again (the overlap); the restored one is new.
        assertThat(ids(restored.get("shifts"))).contains(removed.getId()).doesNotContain(untouched.getId());
        assertThat(restored.get("deleted")).isEmpty();
        assertThat(updatedAt(removed.getId())).isEqualTo(updatedBefore);
    }

    @Test
    void aSaveCommittedWithAnEarlierTimeInsideTheOverlapIsStillSent() throws Exception {
        save(driver, "DXX1", LocalDate.of(2026, 11, 9), "70", "0");
        testClock.advance(Duration.ofMinutes(10));
        save(driver, "DXX1", LocalDate.of(2026, 11, 10), "71", "0");
        String cursor = body(sync(token, null)).get("cursor").asText();

        // A slower transaction stamped two minutes before the cursor commits only now.
        testClock.advance(Duration.ofMinutes(-2));
        Shift late = save(driver, "DXX2", LocalDate.of(2026, 11, 11), "72", "0");
        testClock.advance(Duration.ofMinutes(2));

        JsonNode delta = body(sync(token, cursor));
        assertThat(delta.get("full").asBoolean()).isFalse();
        assertThat(ids(delta.get("shifts"))).contains(late.getId());
    }

    @Test
    void removalsThatLeaveNothingToReportMakeOlderCursorsReloadEverything() throws Exception {
        Shift kept = save(driver, "DXX1", LocalDate.of(2026, 11, 9), "70", "0");
        Shift gone = save(driver, "DXX1", LocalDate.of(2026, 11, 10), "71", "0");
        String cursor = body(sync(token, null)).get("cursor").asText();

        testClock.advance(Duration.ofMinutes(10));
        shiftService.deleteShift(EMAIL, gone.getId());
        shiftService.permanentlyDelete(EMAIL, gone.getId());
        JsonNode afterPermanent = body(sync(token, cursor));
        assertThat(afterPermanent.get("full").asBoolean()).isTrue();
        assertThat(ids(afterPermanent.get("shifts"))).containsExactly(kept.getId());
        testClock.advance(Duration.ofMinutes(10));
        Shift alsoGone = save(driver, "DXX2", LocalDate.of(2026, 11, 11), "72", "0");
        String beforeEmpty = body(sync(token, afterPermanent.get("cursor").asText())).get("cursor").asText();
        testClock.advance(Duration.ofMinutes(10));
        shiftService.deleteShift(EMAIL, alsoGone.getId());
        shiftService.emptyTrash(EMAIL);
        JsonNode afterEmpty = body(sync(token, beforeEmpty));
        assertThat(afterEmpty.get("full").asBoolean()).isTrue();
        assertThat(ids(afterEmpty.get("shifts"))).containsExactly(kept.getId());

        // Once a later change moves the cursor past the reset, syncs are deltas again.
        testClock.advance(Duration.ofMinutes(10));
        Shift added = save(driver, "DXX3", LocalDate.of(2026, 11, 12), "73", "0");
        String settled = body(sync(token, afterEmpty.get("cursor").asText())).get("cursor").asText();
        testClock.advance(Duration.ofMinutes(10));
        added.setTips(new BigDecimal("1.00"));
        shifts.saveAndFlush(added);
        JsonNode delta = body(sync(token, settled));
        assertThat(delta.get("full").asBoolean()).isFalse();
        assertThat(ids(delta.get("shifts"))).containsExactly(added.getId());
    }

    @Test
    void anAccountWithoutResetsKeepsDeltasAndAnOtherAccountsResetDoesNotAffectIt() throws Exception {
        save(driver, "DXX1", LocalDate.of(2026, 11, 9), "70", "0");
        String cursor = body(sync(token, null)).get("cursor").asText();
        Shift otherGone = save(other, "DXX9", LocalDate.of(2026, 11, 9), "70", "0");
        shiftService.deleteShift(OTHER, otherGone.getId());
        shiftService.permanentlyDelete(OTHER, otherGone.getId());
        testClock.advance(Duration.ofMinutes(10));
        assertThat(body(sync(token, cursor)).get("full").asBoolean()).isFalse();
    }

    @Test
    void cursorsOlderThanTwentyNineDaysReloadEverything() throws Exception {
        Shift block = save(driver, "DXX1", LocalDate.of(2026, 11, 9), "70", "0");
        String cursor = body(sync(token, null)).get("cursor").asText();
        testClock.advance(Duration.ofDays(28));
        assertThat(body(sync(token, cursor)).get("full").asBoolean()).isFalse();
        testClock.advance(Duration.ofDays(1).plusMinutes(1));
        JsonNode stale = body(sync(token, cursor));
        assertThat(stale.get("full").asBoolean()).isTrue();
        assertThat(ids(stale.get("shifts"))).containsExactly(block.getId());
    }

    @Test
    void anUnreadableCursorIsInvalid() throws Exception {
        for (String cursor : List.of("not a cursor", "djE6YWJj", "dj I6MTox", "x".repeat(65))) {
            MvcResult result = mvc.perform(get("/api/v1/shifts").param("updatedSince", cursor).header("Authorization",
                    "Bearer " + token)).andExpect(status().isBadRequest()).andReturn();
            assertThat(body(result).get("error").asText()).isEqualTo("invalid");
        }
    }

    @Test
    void anUnchangedAccountAnswersTheSameAndThenNotModified() throws Exception {
        save(driver, "DXX1", LocalDate.of(2026, 11, 9), "70", "0");
        MvcResult first = sync(token, null);
        testClock.advance(Duration.ofMinutes(1));
        MvcResult second = sync(token, null);
        assertThat(second.getResponse().getContentAsString()).isEqualTo(first.getResponse().getContentAsString());
        String etag = first.getResponse().getHeader("ETag");
        assertThat(etag).startsWith("\"").doesNotStartWith("W/");
        assertThat(first.getResponse().getHeader("Cache-Control")).isEqualTo("private, no-cache");

        for (String path : List.of("/api/v1/shifts", "/api/v1/home")) {
            MvcResult fresh = mvc.perform(get(path).header("Authorization", "Bearer " + token))
                    .andExpect(status().isOk()).andReturn();
            MvcResult cached = mvc.perform(get(path).header("Authorization", "Bearer " + token)
                            .header("If-None-Match", fresh.getResponse().getHeader("ETag")))
                    .andExpect(status().isNotModified()).andReturn();
            assertThat(cached.getResponse().getContentAsByteArray()).isEmpty();
            assertThat(cached.getResponse().getHeader("ETag")).isEqualTo(fresh.getResponse().getHeader("ETag"));
        }
        // A change gives a new tag, so the old one no longer matches.
        save(driver, "DXX2", LocalDate.of(2026, 11, 10), "71", "0");
        mvc.perform(get("/api/v1/shifts").header("Authorization", "Bearer " + token).header("If-None-Match", etag))
                .andExpect(status().isOk());
    }

    @Test
    void oneBlockIsReturnedOnlyToItsOwnerAndNotFromRecentlyDeleted() throws Exception {
        Shift mine = save(driver, "DXX1", LocalDate.of(2026, 11, 9), "70", "0");
        Shift theirs = save(other, "DXX9", LocalDate.of(2026, 11, 9), "70", "0");
        Shift deleted = save(driver, "DXX2", LocalDate.of(2026, 11, 10), "71", "0");
        shiftService.deleteShift(EMAIL, deleted.getId());

        JsonNode one = body(mvc.perform(get("/api/v1/shifts/" + mine.getId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(header().exists("ETag")).andReturn());
        assertThat(one.get("id").asLong()).isEqualTo(mine.getId());
        assertThat(one.get("basePay").asText()).isEqualTo("70.00");
        for (long id : new long[]{theirs.getId(), deleted.getId(), 999_999L}) {
            MvcResult missing = mvc.perform(get("/api/v1/shifts/" + id).header("Authorization", "Bearer " + token))
                    .andExpect(status().isNotFound()).andReturn();
            assertThat(body(missing).get("error").asText()).isEqualTo("not_found");
        }
        mvc.perform(get("/api/v1/shifts/abc").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void homeUsesTheAccountsTimeZoneAndTheWebsitesFiguresWithoutScheduledBlocks() throws Exception {
        driver.setTimeZone("America/New_York");
        users.save(driver);
        // 22:00 on Friday in New York is already Saturday in UTC.
        testClock.advance(Duration.ofHours(12));
        Shift monday = save(driver, "DXX1", LocalDate.of(2026, 11, 9), "72", "18.5");
        monday.setMiles(new BigDecimal("40"));
        shifts.saveAndFlush(monday);
        Shift noMiles = save(driver, "DXX1", LocalDate.of(2026, 11, 12), "80", "0");
        Shift lastWeek = save(driver, "DXX2", LocalDate.of(2026, 11, 7), "60", "0");
        Shift scheduled = save(driver, "DXX1", LocalDate.of(2026, 11, 14), "90", "0");
        scheduled.setStatus(ShiftStatus.SCHEDULED);
        shifts.saveAndFlush(scheduled);
        save(other, "DXX9", LocalDate.of(2026, 11, 10), "500", "0");

        JsonNode home = body(mvc.perform(get("/api/v1/home").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
        assertThat(home.get("today").asText()).isEqualTo("2026-11-13");
        JsonNode week = home.get("week");
        assertThat(week.get("from").asText()).isEqualTo("2026-11-09");
        assertThat(week.get("to").asText()).isEqualTo("2026-11-15");
        assertThat(week.get("totalShifts").asInt()).isEqualTo(2);
        assertThat(week.get("totalEarnings").asText()).isEqualTo("170.50");
        assertThat(week.get("scheduledShifts").asInt()).isEqualTo(1);
        var website = reports.statistics(EMAIL,
                ShiftFilter.report(LocalDate.of(2026, 11, 9), LocalDate.of(2026, 11, 15), null, null));
        assertThat(new BigDecimal(week.get("netEarnings").asText()))
                .isEqualByComparingTo(website.getNetEarnings());
        assertThat(new BigDecimal(week.get("netHourlyRate").asText()))
                .isEqualByComparingTo(website.getNetHourlyRate());
        assertThat(week.get("blocksWithoutMiles").asInt()).isEqualTo(website.getBlocksWithoutMiles());

        JsonNode seven = home.get("lastSevenDays");
        assertThat(seven.get("from").asText()).isEqualTo("2026-11-07");
        assertThat(seven.get("to").asText()).isEqualTo("2026-11-13");
        assertThat(seven.get("totalEarnings").asText()).isEqualTo("230.50");
        assertThat(ids(home.get("missingMiles"))).containsExactlyInAnyOrder(noMiles.getId(), lastWeek.getId());
        assertThat(home.get("goals").get("basis").asText()).isNotBlank();
        assertThat(home.get("goals").get("week").isNull()).isTrue();
        assertThat(ids(home.get("missingMiles"))).doesNotContain(scheduled.getId());
    }

    private AppUser account(String name, String email) {
        return users.save(new AppUser(name, email, encoder.encode(PASSWORD)));
    }

    @Test
    void apiWritesFollowTheCompleteLifecycleAndChangeOnlySync() throws Exception {
        driver.setTimeZone("America/New_York");
        users.save(driver);
        save(driver, "OLD1", LocalDate.of(2026, 11, 9), "10", "0");
        testClock.advance(Duration.ofMinutes(10));
        String cursor = body(sync(token, null)).get("cursor").asText();
        testClock.advance(Duration.ofMinutes(10));
        String createKey = key();
        MvcResult created = write(post("/api/v1/shifts"), createKey, block("scheduled", "72", "0", null), 201);
        long id = body(created).get("id").asLong();
        assertThat(created.getResponse().getHeader("Location")).isEqualTo("/api/v1/shifts/" + id);
        assertThat(body(write(post("/api/v1/shifts"), createKey, block("scheduled", "72", "0", null), 201))
                .get("id").asLong()).isEqualTo(id);
        assertThat(shifts.countByOwnerEmailIgnoreCase(EMAIL)).isEqualTo(2);
        var delta = body(sync(token, cursor));
        assertThat(delta.get("full").asBoolean()).isFalse();
        assertThat(ids(delta.get("shifts"))).contains(id);
        assertThat(read(id).get("earnedPay").asText()).isEqualTo("0.00");

        testClock.advance(Duration.ofMinutes(1));
        String startKey = key();
        String startBody = version(id);
        write(post("/api/v1/shifts/" + id + "/start"), startKey, startBody, 200);
        assertThat(read(id).get("details").get("actualStart").asText()).isEqualTo("10:21");
        testClock.advance(Duration.ofMinutes(1));
        String complete = "{\"status\":\"completed\",\"tips\":\"8.50\",\"expectedUpdatedAt\":\"" + updatedAt(id)
                + "\",\"details\":{\"actualStart\":\"10:21\",\"actualEnd\":\"12:50\","
                + "\"odometerStart\":\"100.0\",\"odometerEnd\":\"140.5\",\"stops\":20,\"packages\":25,\"returns\":1}}";
        String completeKey = key();
        write(patch("/api/v1/shifts/" + id + "/status"), completeKey, complete, 200);
        write(post("/api/v1/shifts/" + id + "/start"), startKey, startBody, 200);
        assertThat(read(id).get("details").get("actualEnd").asText()).isEqualTo("12:50");
        assertThat(read(id).get("earnedPay").asText()).isEqualTo("80.50");
        assertThat(read(id).get("miles").asText()).isEqualTo("40.5");
        testClock.advance(Duration.ofMinutes(1));
        write(post("/api/v1/shifts/" + id + "/not-worked"), key(), version(id), 200);
        write(patch("/api/v1/shifts/" + id + "/status"), completeKey, complete, 200);
        JsonNode scheduled = read(id);
        assertThat(scheduled.get("status").asText()).isEqualTo("scheduled");
        assertThat(scheduled.get("tips").asText()).isEqualTo("0.00");
        assertThat(scheduled.get("miles").isNull()).isTrue();
        assertThat(scheduled.get("details").get("actualStart").isNull()).isTrue();
        assertThat(scheduled.get("details").get("odometerStart").isNull()).isTrue();
        assertThat(scheduled.get("details").get("stops").isNull()).isTrue();

        String seen = updatedAt(id).toString();
        String deleteKey = key();
        testClock.advance(Duration.ofMinutes(10));
        write(delete("/api/v1/shifts/" + id).param("expectedUpdatedAt", seen), deleteKey, null, 204);
        var deleted = body(sync(token, delta.get("cursor").asText()));
        assertThat(ids(deleted.get("deleted"))).containsExactly(id);
        testClock.advance(Duration.ofMinutes(10));
        String restoreKey = key();
        String restoreBody = "{\"expectedUpdatedAt\":\"" + seen + "\"}";
        write(post("/api/v1/shifts/" + id + "/restore"), restoreKey, restoreBody, 200);
        var restored = body(sync(token, deleted.get("cursor").asText()));
        assertThat(ids(restored.get("shifts"))).containsExactly(id);
        assertThat(restored.get("deleted")).isEmpty();
        // Delayed delete retries acknowledge delivery without deleting the restored block again.
        write(delete("/api/v1/shifts/" + id).param("expectedUpdatedAt", seen), deleteKey, null, 204);
        assertThat(read(id).get("status").asText()).isEqualTo("scheduled");
        write(post("/api/v1/shifts/" + id + "/restore"), restoreKey, restoreBody, 200);
    }

    @Test
    void staleEditsConflictAndDelayedRetriesNeverOverwriteLaterChanges() throws Exception {
        Shift block = save(driver, "DXX1", LocalDate.of(2026, 11, 10), "72", "0");
        long id = block.getId();
        String seen = updatedAt(id).toString();
        String editKey = key();
        String first = block("completed", "80", "1", seen);
        testClock.advance(Duration.ofMinutes(1));
        write(put("/api/v1/shifts/" + id), editKey, first, 200);
        testClock.advance(Duration.ofMinutes(1));
        write(put("/api/v1/shifts/" + id), key(), block("completed", "90", "2", updatedAt(id).toString()), 200);
        String latest = updatedAt(id).toString();
        write(put("/api/v1/shifts/" + id), editKey, first, 200);
        assertThat(read(id).get("basePay").asText()).isEqualTo("90.00");
        assertThat(updatedAt(id).toString()).isEqualTo(latest);
        String failedKey = key();
        var conflict = body(write(put("/api/v1/shifts/" + id), failedKey, first, 409));
        assertThat(conflict.get("error").asText()).isEqualTo("conflict");
        assertThat(conflict.get("current").get("basePay").asText()).isEqualTo("90.00");
        assertThat(conflict.get("current").get("status").asText()).isEqualTo("completed");
        // A failed action didn't reserve the key; the driver can resolve and submit it.
        write(put("/api/v1/shifts/" + id), failedKey, block("completed", "95", "2", latest), 200);
        assertThat(body(write(put("/api/v1/shifts/" + id), editKey,
                block("completed", "100", "2", latest), 409)).get("error").asText()).isEqualTo("idempotency_conflict");
    }

    @Test
    void mutationVersionsAndValidationUseStructuredErrorsAndDoNotWrite() throws Exception {
        String payload = block("scheduled", "72", "0", null);
        for (String invalidKey : List.of("", "bad", "a".repeat(100))) {
            assertThat(body(write(post("/api/v1/shifts"), invalidKey, payload, 400)).get("error").asText())
                    .isEqualTo("invalid");
        }
        mvc.perform(post("/api/v1/shifts").header("Authorization", "Bearer " + token)
                .contentType("application/json").content(payload)).andExpect(status().isBadRequest());
        for (String invalid : List.of("{}", "{", "null", block("unknown", "72", "0", null),
                block("scheduled", "72", "5", null), block("completed", "-1", "0", null),
                payload.replace("DXX1", " "), payload.replace("2026-11-13", "2026-99-99"),
                payload.replace("\"tips\":\"0\"", "\"tips\":null"))) {
            assertThat(body(write(post("/api/v1/shifts"), key(), invalid, 400)).get("error").asText())
                    .isEqualTo("invalid");
        }
        assertThat(shifts.count()).isZero();
        long id = body(write(post("/api/v1/shifts"), key(), payload, 201)).get("id").asLong();
        write(put("/api/v1/shifts/" + id), key(), payload, 400);
        write(post("/api/v1/shifts/" + id + "/start"), key(), "{}", 400);
        write(post("/api/v1/shifts/" + id + "/not-worked"), key(), version(id), 400);
        write(patch("/api/v1/shifts/" + id + "/status"), key(),
                "{\"status\":\"completed\",\"miles\":\"1.23\",\"expectedUpdatedAt\":\"" + updatedAt(id) + "\"}", 400);
        write(patch("/api/v1/shifts/" + id + "/status"), key(),
                "{\"status\":\"completed\",\"details\":{\"stops\":-1},\"expectedUpdatedAt\":\"" + updatedAt(id) + "\"}", 400);
        assertThat(read(id).get("status").asText()).isEqualTo("scheduled");
    }

    @Test
    void allMutationsRequireBearerAndRefuseOtherAccountsAndUnknownIds() throws Exception {
        Shift theirs = save(other, "DXX9", LocalDate.of(2026, 11, 13), "72", "0");
        for (long id : new long[]{theirs.getId(), 999_999L}) {
            var requests = List.of(put("/api/v1/shifts/" + id), patch("/api/v1/shifts/" + id + "/status"),
                    post("/api/v1/shifts/" + id + "/start"), post("/api/v1/shifts/" + id + "/not-worked"),
                    post("/api/v1/shifts/" + id + "/restore"),
                    delete("/api/v1/shifts/" + id).param("expectedUpdatedAt", START.toString()));
            for (var request : requests) {
                mvc.perform(request.contentType("application/json").content(block("completed", "72", "0", START.toString())))
                        .andExpect(status().isUnauthorized());
                mvc.perform(request.with(user(EMAIL))).andExpect(status().isUnauthorized());
                write(request, key(), block("completed", "72", "0", START.toString()), 404);
            }
        }
        mvc.perform(post("/api/v1/shifts").contentType("application/json").content(block("completed", "72", "0", null)))
                .andExpect(status().isUnauthorized());
        assertThat(shifts.count()).isEqualTo(1);
    }

    @Test
    void restoredAndDeletedAndPermanentlyRemovedBlocksDoNotGetRecreatedByRetries() throws Exception {
        String createKey = key();
        String input = block("completed", "72", "0", null);
        long id = body(write(post("/api/v1/shifts"), createKey, input, 201)).get("id").asLong();
        String seen = updatedAt(id).toString();
        shiftService.deleteShift(EMAIL, id);
        write(post("/api/v1/shifts"), createKey, input, 201);
        assertThat(shifts.count()).isZero();
        // Exactly 30 days is outside the restore window, even before the purge job has run.
        testClock.advance(Duration.ofDays(30));
        token = signIn(EMAIL);
        write(post("/api/v1/shifts/" + id + "/restore"), key(), "{\"expectedUpdatedAt\":\"" + seen + "\"}", 404);
        shiftService.permanentlyDelete(EMAIL, id);
        write(post("/api/v1/shifts"), createKey, input, 201);
        assertThat(shifts.count()).isZero();
    }

    @Test
    void parallelCreatesAndKeysFromDifferentAccountsAreIndependent() throws Exception {
        String createKey = key();
        String payload = block("completed", "72", "0", null);
        try (var executor = java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var gate = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.Callable<Long> action = () -> {
                gate.await();
                return body(write(post("/api/v1/shifts"), createKey, payload, 201)).get("id").asLong();
            };
            var a = executor.submit(action);
            var b = executor.submit(action);
            gate.countDown();
            assertThat(a.get(20, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(b.get(20, java.util.concurrent.TimeUnit.SECONDS));
        }
        assertThat(shifts.countByOwnerEmailIgnoreCase(EMAIL)).isEqualTo(1);
        mvc.perform(post("/api/v1/shifts").header("Authorization", "Bearer " + otherToken)
                .header("Idempotency-Key", createKey).contentType("application/json").content(payload))
                .andExpect(status().isCreated());
        assertThat(shifts.countByOwnerEmailIgnoreCase(OTHER)).isEqualTo(1);
        // Keys belong to an action as well as a payload.
        long id = shifts.findAllByOwnerEmailIgnoreCaseOrderByDateDescStartTimeDesc(EMAIL).getFirst().getId();
        assertThat(body(write(post("/api/v1/shifts/" + id + "/not-worked"), createKey, version(id), 409))
                .get("error").asText()).isEqualTo("idempotency_conflict");
    }

    private static String key() { return java.util.UUID.randomUUID().toString(); }

    @Test
    void websiteChangesConflictWithEveryPendingMutationAndBusinessFailuresRollBack() throws Exception {
        Shift block = save(driver, "DXX1", LocalDate.of(2026, 11, 13), "72", "0");
        long id = block.getId();
        String old = updatedAt(id).toString();
        testClock.advance(Duration.ofMinutes(1));
        var websiteEdit = new com.angel.flexbuddy.dto.UpdateShiftRequest("WEB1", block.getDate(),
                block.getStartTime(), block.getEndTime(), new BigDecimal("80"), BigDecimal.ZERO);
        shiftService.updateShift(EMAIL, id, websiteEdit);
        for (var request : List.of(put("/api/v1/shifts/" + id), patch("/api/v1/shifts/" + id + "/status"),
                post("/api/v1/shifts/" + id + "/start"), post("/api/v1/shifts/" + id + "/not-worked"),
                delete("/api/v1/shifts/" + id).param("expectedUpdatedAt", old))) {
            var conflict = body(write(request, key(), block("completed", "90", "0", old), 409));
            assertThat(conflict.get("error").asText()).isEqualTo("conflict");
            assertThat(conflict.get("current").get("station").asText()).isEqualTo("WEB1");
        }
        String good = block("completed", "90", "0", updatedAt(id).toString());
        String bad = good.substring(0, good.length() - 1) + ",\"details\":{\"packages\":1,\"returns\":2}}";
        String actionKey = key();
        write(put("/api/v1/shifts/" + id), actionKey, bad, 400);
        assertThat(read(id).get("station").asText()).isEqualTo("WEB1");
        assertThat(read(id).get("basePay").asText()).isEqualTo("80.00");
        write(put("/api/v1/shifts/" + id), actionKey, good, 200);
        assertThat(read(id).get("basePay").asText()).isEqualTo("90.00");
    }

    @Test
    void apiStartAndRescheduleKeepWebsiteTimingAndStatusRules() throws Exception {
        long id = body(write(post("/api/v1/shifts"), key(), block("scheduled", "72", "0", null), 201))
                .get("id").asLong();
        write(post("/api/v1/shifts/" + id + "/start"), key(), version(id), 400);
        testClock.advance(Duration.ofMinutes(15));
        write(post("/api/v1/shifts/" + id + "/start"), key(), version(id), 200);
        testClock.advance(Duration.ofMinutes(1));
        write(patch("/api/v1/shifts/" + id + "/status"), key(),
                "{\"status\":\"completed\",\"expectedUpdatedAt\":\"" + updatedAt(id) + "\"}", 200);
        write(patch("/api/v1/shifts/" + id + "/status"), key(),
                "{\"status\":\"scheduled\",\"expectedUpdatedAt\":\"" + updatedAt(id) + "\"}", 400);
        testClock.advance(Duration.ofMinutes(1));
        write(post("/api/v1/shifts/" + id + "/not-worked"), key(), version(id), 200);
        testClock.advance(Duration.ofHours(4));
        write(post("/api/v1/shifts/" + id + "/start"), key(), version(id), 400);
        write(patch("/api/v1/shifts/" + id + "/status"), key(),
                "{\"status\":\"forfeited\",\"expectedUpdatedAt\":\"" + updatedAt(id) + "\"}", 200);
        assertThat(read(id).get("earnedPay").asText()).isEqualTo("0.00");
        assertThat(read(id).get("lateForfeit").asBoolean()).isTrue();
        write(patch("/api/v1/shifts/" + id + "/status"), key(),
                "{\"status\":\"scheduled\",\"basePay\":\"72\",\"expectedUpdatedAt\":\"" + updatedAt(id) + "\"}", 400);
    }

    private static String block(String status, String base, String tips, String expected) {
        return "{\"station\":\"DXX1\",\"date\":\"2026-11-13\",\"startTime\":\"10:30\",\"endTime\":\"13:30\","
                + "\"basePay\":\"" + base + "\",\"tips\":\"" + tips + "\",\"status\":\"" + status + "\""
                + (expected == null ? "" : ",\"expectedUpdatedAt\":\"" + expected + "\"") + "}";
    }

    private String version(long id) { return "{\"expectedUpdatedAt\":\"" + updatedAt(id) + "\"}"; }

    private JsonNode read(long id) throws Exception {
        return body(mvc.perform(get("/api/v1/shifts/" + id).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
    }

    private MvcResult write(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String key, String payload, int expectedStatus) throws Exception {
        request.header("Authorization", "Bearer " + token).header("Idempotency-Key", key);
        if (payload != null) request.contentType("application/json").content(payload);
        return mvc.perform(request).andExpect(status().is(expectedStatus)).andReturn();
    }

    private String signIn(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/sign-in").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return body(result).get("token").asText();
    }

    private Shift save(AppUser owner, String station, LocalDate date, String base, String tips) {
        Shift shift = new Shift(null, station, date, LocalTime.of(18, 30), LocalTime.of(21, 30),
                new BigDecimal(base), new BigDecimal(tips), owner);
        return shifts.saveAndFlush(shift);
    }

    private MvcResult sync(String bearer, String cursor) throws Exception {
        var request = get("/api/v1/shifts").header("Authorization", "Bearer " + bearer);
        if (cursor != null) request = request.param("updatedSince", cursor);
        return mvc.perform(request).andExpect(status().isOk()).andReturn();
    }

    private Instant updatedAt(Long id) {
        return shifts.findAllIncludingDeleted(EMAIL).stream().filter(shift -> shift.getId().equals(id))
                .findFirst().orElseThrow().getUpdatedAt();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private static List<Long> ids(JsonNode array) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode node : array) ids.add(node.isObject() ? node.get("id").asLong() : node.asLong());
        return ids;
    }
}
