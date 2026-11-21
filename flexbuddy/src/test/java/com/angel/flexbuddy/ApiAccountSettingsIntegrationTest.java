package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.GoalBasis;
import com.angel.flexbuddy.model.VehicleCostMethod;
import com.angel.flexbuddy.repository.AppTokenRepository;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.security.MutableClock;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** FB-059: real bearer authentication and synthetic accounts; no production data. */
@SpringBootTest(properties = {"flexbuddy.api.enabled=true", "flexbuddy.security.remember-me-key=api-test-key"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiAccountSettingsIntegrationTest {
    private static final String PATH = "/api/v1/account/settings";
    private static final String EMAIL = "account-settings-driver@example.test";
    private static final String OTHER = "account-settings-other@example.test";
    private static final String PASSWORD = "account-settings-test-password";
    private static final Instant START = Instant.parse("2026-06-30T16:00:00Z");
    private static final List<String> KEYS = List.of("language", "timeZone", "vehicleCostMethod", "customMileageRate",
            "weeklyGoal", "monthlyGoal", "goalBasis", "payoutDays", "payoutLagDays", "taxSetAsidePercent", "remindTax",
            "remindBeforeMinutes", "remindConfirm", "remindMiles", "askMissingMiles", "forfeitCutoffMinutes");

    @TestConfiguration
    static class Beans {
        @Bean @Primary MutableClock accountSettingsClock() { return new MutableClock(START); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired AppTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired JdbcTemplate jdbc;

    private AppUser driver;
    private AppUser other;
    private String token;
    private String otherToken;

    @BeforeEach
    void before() throws Exception {
        wipe();
        driver = users.saveAndFlush(new AppUser("Settings driver", EMAIL, encoder.encode(PASSWORD)));
        other = users.saveAndFlush(new AppUser("Other settings", OTHER, encoder.encode(PASSWORD)));
        token = signIn(EMAIL);
        otherToken = signIn(OTHER);
    }

    @AfterEach
    void wipe() {
        tokens.deleteAll();
        users.deleteAll();
        clock.advance(Duration.between(clock.instant(), START));
    }

    @Test
    void readHasExactlyTheDocumentedKeysMatchesTheWebsiteAndNeverTheCalendarLink() throws Exception {
        driver.setCalendarToken("synthetic-private-feed-marker");
        users.saveAndFlush(driver);
        assertDefaultsMatchWebsite();

        driver.setLanguage("es");
        driver.setTimeZone("America/Chicago");
        driver.setMileageRate(new BigDecimal("0.655"));
        driver.setVehicleCostMethod(VehicleCostMethod.ACTUAL_EXPENSES);
        driver.setWeeklyGoal(new BigDecimal("600"));
        driver.setGoalBasis(GoalBasis.NET);
        driver.setTaxSetAsidePercent(new BigDecimal("25"));
        driver.setRemindTax(true);
        driver.setRemindBeforeMinutes(60);
        driver.setRemindConfirm(true);
        driver.setRemindMiles(true);
        driver.setAskMissingMiles(false);
        driver.setForfeitCutoffMinutes(90);
        driver.setPayoutDays("FRIDAY,MONDAY");
        driver.setPayoutLagDays(2);
        users.saveAndFlush(driver);

        MvcResult result = read(token);
        JsonNode api = body(result);
        JsonNode website = website();
        assertThat(api.propertyNames()).containsExactlyInAnyOrderElementsOf(KEYS);
        assertThat(api.get("language").asText()).isEqualTo("es").isEqualTo(website.get("language").asText());
        assertThat(api.get("timeZone").asText()).isEqualTo("America/Chicago");
        assertThat(api.get("vehicleCostMethod").asText()).isEqualTo("actual_expenses");
        assertThat(api.get("customMileageRate").asText()).isEqualTo("0.655");
        assertThat(api.get("weeklyGoal").asText()).isEqualTo("600.00");
        assertThat(api.get("monthlyGoal").isNull()).isTrue();
        assertThat(api.get("goalBasis").asText()).isEqualTo("net");
        assertThat(api.get("payoutDays")).isEqualTo(json.readTree("[\"monday\",\"friday\"]"));
        assertThat(api.get("payoutLagDays").asInt()).isEqualTo(2);
        assertThat(new BigDecimal(api.get("taxSetAsidePercent").asText())).isEqualByComparingTo("25");
        assertThat(api.get("taxSetAsidePercent").isString()).isTrue();
        assertThat(api.get("remindTax").asBoolean()).isTrue();
        assertThat(api.get("remindBeforeMinutes").asInt()).isEqualTo(60);
        assertThat(api.get("remindConfirm").asBoolean()).isTrue();
        assertThat(api.get("remindMiles").asBoolean()).isTrue();
        assertThat(api.get("askMissingMiles").asBoolean()).isFalse();
        assertThat(api.get("forfeitCutoffMinutes").asInt()).isEqualTo(90);
        for (String key : List.of("timeZone", "payoutLagDays", "remindTax", "remindBeforeMinutes", "remindConfirm",
                "remindMiles", "askMissingMiles", "forfeitCutoffMinutes")) {
            assertThat(api.get(key).asText()).as(key).isEqualTo(website.get(key).asText());
        }
        assertThat(result.getResponse().getContentAsString()).doesNotContain("synthetic-private-feed-marker",
                "/calendar/", PASSWORD);
        assertThat(users.findById(driver.getId()).orElseThrow().getCalendarToken())
                .isEqualTo("synthetic-private-feed-marker");
    }

    private void assertDefaultsMatchWebsite() throws Exception {
        JsonNode api = body(read(token));
        JsonNode website = website();
        assertThat(api.propertyNames()).containsExactlyInAnyOrderElementsOf(KEYS);
        assertThat(api.get("language").isNull()).isTrue();
        assertThat(api.get("timeZone").asText()).isEqualTo(AppUser.DEFAULT_TIME_ZONE);
        assertThat(api.get("vehicleCostMethod").asText()).isEqualTo("standard_mileage");
        assertThat(api.get("customMileageRate").isNull()).isTrue();
        assertThat(api.get("taxSetAsidePercent").isNull()).isTrue();
        assertThat(api.get("remindBeforeMinutes").isNull()).isTrue();
        assertThat(api.get("forfeitCutoffMinutes").asInt()).isEqualTo(AppUser.DEFAULT_FORFEIT_CUTOFF_MINUTES);
        assertThat(api.get("payoutDays")).isEqualTo(json.readTree("[\"tuesday\",\"friday\"]"));
        for (String key : List.of("remindTax", "remindConfirm", "remindMiles", "askMissingMiles")) {
            assertThat(api.get(key).asBoolean()).as(key).isEqualTo(website.get(key).asBoolean());
        }
    }

    @Test
    void eachGroupSavesReturnsTheReadsBodyAndTagAndTheWebsiteSeesIt() throws Exception {
        Map<String, String> writes = new java.util.LinkedHashMap<>();
        writes.put("language", "{\"language\":\"es\"}");
        writes.put("time-zone", "{\"timeZone\":\"America/Los_Angeles\"}");
        writes.put("goals", "{\"weeklyGoal\":\"600\",\"monthlyGoal\":\"2500.5\",\"goalBasis\":\"net\"}");
        writes.put("tax", "{\"taxSetAsidePercent\":\"25\",\"remindTax\":true}");
        writes.put("reminders", "{\"remindBeforeMinutes\":120,\"remindConfirm\":true,\"remindMiles\":false,"
                + "\"askMissingMiles\":false,\"forfeitCutoffMinutes\":30}");
        writes.put("mileage", "{\"vehicleCostMethod\":\"actual_expenses\",\"customMileageRate\":\"0.7\"}");
        writes.put("payouts", "{\"payoutDays\":[\"friday\",\"monday\"],\"payoutLagDays\":3}");
        String tag = etag(read(token));
        for (var write : writes.entrySet()) {
            MvcResult saved = write(write.getKey(), write.getValue(), tag, token).andExpect(status().isOk())
                    .andExpect(header().string("Cache-Control", "private, no-cache")).andReturn();
            assertThat(etag(saved)).as(write.getKey()).isNotEqualTo(tag);
            // The answer is exactly what the next read from the database sends, so the phone's tag stays good.
            MvcResult reread = read(token);
            assertThat(etag(reread)).as(write.getKey()).isEqualTo(etag(saved));
            assertThat(saved.getResponse().getContentAsString()).isEqualTo(reread.getResponse().getContentAsString());
            tag = etag(saved);
        }
        JsonNode api = body(read(token));
        assertThat(api.get("language").asText()).isEqualTo("es");
        assertThat(api.get("timeZone").asText()).isEqualTo("America/Los_Angeles");
        assertThat(api.get("weeklyGoal").asText()).isEqualTo("600.00");
        assertThat(api.get("monthlyGoal").asText()).isEqualTo("2500.50");
        assertThat(api.get("goalBasis").asText()).isEqualTo("net");
        assertThat(api.get("taxSetAsidePercent").asText()).isEqualTo("25.00");
        assertThat(api.get("remindTax").asBoolean()).isTrue();
        assertThat(api.get("remindBeforeMinutes").asInt()).isEqualTo(120);
        assertThat(api.get("remindConfirm").asBoolean()).isTrue();
        assertThat(api.get("remindMiles").asBoolean()).isFalse();
        assertThat(api.get("askMissingMiles").asBoolean()).isFalse();
        assertThat(api.get("forfeitCutoffMinutes").asInt()).isEqualTo(30);
        assertThat(api.get("vehicleCostMethod").asText()).isEqualTo("actual_expenses");
        assertThat(api.get("customMileageRate").asText()).isEqualTo("0.700");
        assertThat(api.get("payoutDays")).isEqualTo(json.readTree("[\"monday\",\"friday\"]"));
        assertThat(api.get("payoutLagDays").asInt()).isEqualTo(3);

        JsonNode website = website();
        assertThat(website.get("language").asText()).isEqualTo("es");
        assertThat(website.get("timeZone").asText()).isEqualTo("America/Los_Angeles");
        assertThat(new BigDecimal(website.get("weeklyGoal").asText())).isEqualByComparingTo("600");
        assertThat(website.get("goalBasis").asText()).isEqualTo("NET");
        assertThat(new BigDecimal(website.get("taxSetAsidePercent").asText())).isEqualByComparingTo("25");
        assertThat(website.get("remindBeforeMinutes").asInt()).isEqualTo(120);
        assertThat(website.get("vehicleCostMethod").asText()).isEqualTo("ACTUAL_EXPENSES");
        assertThat(new BigDecimal(website.get("mileageRate").asText())).isEqualByComparingTo("0.7");
        assertThat(website.get("payoutDays")).isEqualTo(json.readTree("[\"MONDAY\",\"FRIDAY\"]"));
    }

    @Test
    void absentNullableFieldsClearAndAbsentRequiredFieldsAreRefused() throws Exception {
        driver.setLanguage("es");
        driver.setWeeklyGoal(new BigDecimal("600"));
        driver.setMonthlyGoal(new BigDecimal("2000"));
        driver.setTaxSetAsidePercent(new BigDecimal("20"));
        driver.setRemindBeforeMinutes(60);
        driver.setMileageRate(new BigDecimal("0.655"));
        users.saveAndFlush(driver);
        String tag = etag(read(token));
        tag = etag(write("language", "{}", tag, token).andExpect(status().isOk()).andReturn());
        tag = etag(write("goals", "{\"goalBasis\":\"gross\"}", tag, token).andExpect(status().isOk()).andReturn());
        tag = etag(write("tax", "{\"taxSetAsidePercent\":null,\"remindTax\":false}", tag, token)
                .andExpect(status().isOk()).andReturn());
        tag = etag(write("reminders", "{\"remindConfirm\":false,\"remindMiles\":true,\"askMissingMiles\":true,"
                + "\"forfeitCutoffMinutes\":45}", tag, token).andExpect(status().isOk()).andReturn());
        write("mileage", "{\"vehicleCostMethod\":\"standard_mileage\"}", tag, token).andExpect(status().isOk());
        JsonNode api = body(read(token));
        for (String key : List.of("language", "weeklyGoal", "monthlyGoal", "taxSetAsidePercent", "remindBeforeMinutes",
                "customMileageRate")) {
            assertThat(api.get(key).isNull()).as(key).isTrue();
        }

        String current = etag(read(token));
        invalid("goals", "{\"weeklyGoal\":\"600.00\"}", current, "goalBasis");
        invalid("tax", "{\"taxSetAsidePercent\":\"20\"}", current, "remindTax");
        invalid("reminders", "{\"remindBeforeMinutes\":60}", current, "remindConfirm", "remindMiles",
                "askMissingMiles", "forfeitCutoffMinutes");
        invalid("mileage", "{\"customMileageRate\":\"0.5\"}", current, "vehicleCostMethod");
        invalid("payouts", "{}", current, "payoutDays", "payoutLagDays");
        invalid("time-zone", "{\"timeZone\":null}", current, "timeZone");
        invalid("reminders", "{\"remindConfirm\":null,\"remindMiles\":true,\"askMissingMiles\":true,"
                + "\"forfeitCutoffMinutes\":45}", current, "remindConfirm");
    }

    @Test
    void theWebsitesLimitsHoldAtBothEdgesAndNothingIsSavedOnARefusal() throws Exception {
        String reminders = "{\"remindBeforeMinutes\":%s,\"remindConfirm\":true,\"remindMiles\":true,"
                + "\"askMissingMiles\":true,\"forfeitCutoffMinutes\":%s}";
        List<String[]> accepted = List.of(
                new String[]{"reminders", reminders.formatted("30", "0")},
                new String[]{"reminders", reminders.formatted("720", "720")},
                new String[]{"reminders", reminders.formatted("null", "45")},
                new String[]{"payouts", "{\"payoutDays\":[\"sunday\"],\"payoutLagDays\":0}"},
                new String[]{"payouts", "{\"payoutDays\":[\"monday\"],\"payoutLagDays\":14}"},
                new String[]{"tax", "{\"taxSetAsidePercent\":\"0.01\",\"remindTax\":true}"},
                new String[]{"tax", "{\"taxSetAsidePercent\":\"60\",\"remindTax\":true}"},
                new String[]{"mileage", "{\"vehicleCostMethod\":\"standard_mileage\",\"customMileageRate\":\"0\"}"},
                new String[]{"mileage", "{\"vehicleCostMethod\":\"standard_mileage\",\"customMileageRate\":\"5.000\"}"},
                new String[]{"goals", "{\"weeklyGoal\":\"0.01\",\"monthlyGoal\":\"99999999.99\",\"goalBasis\":\"gross\"}"},
                new String[]{"language", "{\"language\":\"en\"}"},
                new String[]{"time-zone", "{\"timeZone\":\"Pacific/Honolulu\"}"});
        for (String[] write : accepted) {
            write(write[0], write[1], etag(read(token)), token).andExpect(status().isOk());
        }
        assertThat(new BigDecimal(body(read(token)).get("customMileageRate").asText())).isEqualByComparingTo("5");

        String tag = etag(read(token));
        Map<String, Object> row = jdbc.queryForMap("select * from app_users where id = ?", driver.getId());
        List<String[]> refused = List.of(
                new String[]{"reminders", reminders.formatted("45", "45"), "remindBeforeMinutes"},
                new String[]{"reminders", reminders.formatted("60", "-1"), "forfeitCutoffMinutes"},
                new String[]{"reminders", reminders.formatted("60", "721"), "forfeitCutoffMinutes"},
                new String[]{"reminders", reminders.formatted("60.5", "45"), "remindBeforeMinutes"},
                new String[]{"reminders", reminders.formatted("\"60\"", "45"), "remindBeforeMinutes"},
                new String[]{"reminders", reminders.formatted("60", "99999999999"), "forfeitCutoffMinutes"},
                new String[]{"reminders", "{\"remindBeforeMinutes\":60,\"remindConfirm\":\"true\",\"remindMiles\":true,"
                        + "\"askMissingMiles\":true,\"forfeitCutoffMinutes\":45}", "remindConfirm"},
                new String[]{"payouts", "{\"payoutDays\":[],\"payoutLagDays\":1}", "payoutDays"},
                new String[]{"payouts", "{\"payoutDays\":[\"monday\",\"monday\"],\"payoutLagDays\":1}", "payoutDays"},
                new String[]{"payouts", "{\"payoutDays\":[\"Monday\"],\"payoutLagDays\":1}", "payoutDays"},
                new String[]{"payouts", "{\"payoutDays\":[\"funday\"],\"payoutLagDays\":1}", "payoutDays"},
                new String[]{"payouts", "{\"payoutDays\":[\"fr\\u0131day\"],\"payoutLagDays\":1}", "payoutDays"},
                new String[]{"payouts", "{\"payoutDays\":\"monday\",\"payoutLagDays\":1}", "payoutDays"},
                new String[]{"payouts", "{\"payoutDays\":[\"monday\"],\"payoutLagDays\":-1}", "payoutLagDays"},
                new String[]{"payouts", "{\"payoutDays\":[\"monday\"],\"payoutLagDays\":15}", "payoutLagDays"},
                new String[]{"payouts", "{\"payoutDays\":[\"monday\"],\"payoutLagDays\":\"1\"}", "payoutLagDays"},
                new String[]{"tax", "{\"taxSetAsidePercent\":\"0\",\"remindTax\":true}", "taxSetAsidePercent"},
                new String[]{"tax", "{\"taxSetAsidePercent\":\"60.01\",\"remindTax\":true}", "taxSetAsidePercent"},
                new String[]{"tax", "{\"taxSetAsidePercent\":\"1.234\",\"remindTax\":true}", "taxSetAsidePercent"},
                new String[]{"tax", "{\"taxSetAsidePercent\":25,\"remindTax\":true}", "taxSetAsidePercent"},
                new String[]{"tax", "{\"taxSetAsidePercent\":\"2e1\",\"remindTax\":true}", "taxSetAsidePercent"},
                new String[]{"mileage", "{\"vehicleCostMethod\":\"standard_mileage\",\"customMileageRate\":\"5.001\"}",
                        "customMileageRate"},
                new String[]{"mileage", "{\"vehicleCostMethod\":\"standard_mileage\",\"customMileageRate\":\"0.0001\"}",
                        "customMileageRate"},
                new String[]{"mileage", "{\"vehicleCostMethod\":\"standard_mileage\",\"customMileageRate\":\"-0.1\"}",
                        "customMileageRate"},
                new String[]{"mileage", "{\"vehicleCostMethod\":\"STANDARD_MILEAGE\"}", "vehicleCostMethod"},
                new String[]{"mileage", "{\"vehicleCostMethod\":\"bicycle\"}", "vehicleCostMethod"},
                new String[]{"mileage", "{\"vehicleCostMethod\":\"standard_m\\u0131leage\"}", "vehicleCostMethod"},
                new String[]{"goals", "{\"weeklyGoal\":\"0\",\"goalBasis\":\"gross\"}", "weeklyGoal"},
                new String[]{"goals", "{\"weeklyGoal\":\"-1\",\"goalBasis\":\"gross\"}", "weeklyGoal"},
                new String[]{"goals", "{\"monthlyGoal\":\"1.234\",\"goalBasis\":\"gross\"}", "monthlyGoal"},
                new String[]{"goals", "{\"weeklyGoal\":\"100000000.00\",\"goalBasis\":\"gross\"}", "weeklyGoal"},
                new String[]{"goals", "{\"weeklyGoal\":600,\"goalBasis\":\"gross\"}", "weeklyGoal"},
                new String[]{"goals", "{\"goalBasis\":\"other\"}", "goalBasis"},
                new String[]{"language", "{\"language\":\"fr\"}", "language"},
                new String[]{"language", "{\"language\":\"\"}", "language"},
                new String[]{"language", "{\"language\":1}", "language"},
                new String[]{"time-zone", "{\"timeZone\":\"Mars/Base\"}", "timeZone"},
                new String[]{"time-zone", "{\"timeZone\":\" \"}", "timeZone"});
        for (String[] write : refused) invalid(write[0], write[1], tag, write[2]);
        assertThat(jdbc.queryForMap("select * from app_users where id = ?", driver.getId())).isEqualTo(row);
        assertThat(etag(read(token))).isEqualTo(tag);
    }

    @Test
    void bodiesThatAreNotJsonObjectsAreRefusedAndUnknownGroupsAreNotFound() throws Exception {
        String tag = etag(read(token));
        for (String body : List.of("not json", "[]", "\"goals\"", "")) {
            write("goals", body, tag, token).andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"error\":\"invalid\"}", true));
        }
        write("calendar-token", "{}", tag, token).andExpect(status().isNotFound());
        mvc.perform(put(PATH).header("Authorization", "Bearer " + token).header("If-Match", tag)
                .contentType("application/json").content("{}")).andExpect(status().isMethodNotAllowed());
    }

    @Test
    void ifMatchIsRequiredAndAStaleOneIsAConflictUnlessTheChangeIsAlreadySaved() throws Exception {
        String goals = "{\"weeklyGoal\":\"600.00\",\"monthlyGoal\":null,\"goalBasis\":\"net\"}";
        for (String bad : new String[]{null, "", "*", "W/" + etag(read(token)), "\"abc\""}) {
            var request = put(PATH + "/goals").header("Authorization", "Bearer " + token)
                    .contentType("application/json").content(goals);
            if (bad != null) request.header("If-Match", bad);
            mvc.perform(request).andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"error\":\"invalid\",\"fields\":{\"If-Match\":\"invalid\"}}", true));
        }

        String seen = etag(read(token));
        // The website changes something else between the phone's read and its write.
        mvc.perform(put("/account/time-zone").with(user(EMAIL)).with(csrf()).contentType("application/json")
                .content("{\"timeZone\":\"America/Denver\"}")).andExpect(status().isOk());
        MvcResult conflict = write("goals", goals, seen, token).andExpect(status().isConflict()).andReturn();
        JsonNode body = body(conflict);
        assertThat(body.get("error").asText()).isEqualTo("conflict");
        assertThat(body.get("current").get("timeZone").asText()).isEqualTo("America/Denver");
        assertThat(body.get("current").get("weeklyGoal").isNull()).isTrue();
        MvcResult now = read(token);
        assertThat(etag(conflict)).isEqualTo(etag(now));
        assertThat(body.get("current")).isEqualTo(body(now));
        assertThat(users.findById(driver.getId()).orElseThrow().getWeeklyGoal()).isNull();

        // With the current tag it saves; a retry with the old tag after a lost answer then succeeds unchanged.
        MvcResult saved = write("goals", goals, etag(now), token).andExpect(status().isOk()).andReturn();
        MvcResult retry = write("goals", "{\"weeklyGoal\":\"600\",\"goalBasis\":\"net\"}", etag(now), token)
                .andExpect(status().isOk()).andReturn();
        assertThat(etag(retry)).isEqualTo(etag(saved));
        assertThat(retry.getResponse().getContentAsString()).isEqualTo(saved.getResponse().getContentAsString());
        // The same old tag with different values is still a conflict.
        write("goals", "{\"weeklyGoal\":\"700.00\",\"goalBasis\":\"net\"}", etag(now), token)
                .andExpect(status().isConflict());
        assertThat(users.findById(driver.getId()).orElseThrow().getWeeklyGoal()).isEqualByComparingTo("600");
    }

    @Test
    void remindersKeepTheSavedTimeZone() throws Exception {
        driver.setTimeZone("America/Chicago");
        users.saveAndFlush(driver);
        write("reminders", "{\"remindBeforeMinutes\":30,\"remindConfirm\":false,\"remindMiles\":false,"
                + "\"askMissingMiles\":true,\"forfeitCutoffMinutes\":45,\"timeZone\":\"Europe/Madrid\"}",
                etag(read(token)), token).andExpect(status().isOk());
        assertThat(users.findById(driver.getId()).orElseThrow().getTimeZone()).isEqualTo("America/Chicago");
    }

    @Test
    void conditionalReadsAndChangesFromEitherSideChangeTheTag() throws Exception {
        MvcResult first = read(token);
        assertThat(etag(first)).matches("\"[0-9a-f]{64}\"");
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token).header("If-None-Match", etag(first)))
                .andExpect(status().isNotModified()).andExpect(content().string(""))
                .andExpect(header().string("ETag", etag(first)))
                .andExpect(header().string("Cache-Control", "private, no-cache"));
        mvc.perform(put("/account/tax").with(user(EMAIL)).with(csrf()).contentType("application/json")
                .content("{\"taxSetAsidePercent\":20,\"remindTax\":true}")).andExpect(status().isOk());
        MvcResult website = mvc.perform(get(PATH).header("Authorization", "Bearer " + token)
                .header("If-None-Match", etag(first))).andExpect(status().isOk()).andReturn();
        assertThat(etag(website)).isNotEqualTo(etag(first));
        write("language", "{\"language\":\"es\"}", etag(website), token).andExpect(status().isOk());
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token).header("If-None-Match", etag(website)))
                .andExpect(status().isOk());
    }

    @Test
    void onlyABearerTokenAuthenticatesAndNothingIsReadComparedOrSaved() throws Exception {
        String tag = etag(read(token));
        Map<String, Object> row = jdbc.queryForMap("select * from app_users where id = ?", driver.getId());
        String body = "{\"language\":\"es\"}";
        mvc.perform(get(PATH).header("If-None-Match", tag)).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("ETag"));
        mvc.perform(get(PATH).with(user(EMAIL)).header("If-None-Match", tag)).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("ETag"));
        mvc.perform(put(PATH + "/language").header("If-Match", tag).contentType("application/json").content(body))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("ETag"))
                .andExpect(content().json("{\"error\":\"unauthorized\"}", true));
        mvc.perform(put(PATH + "/language").header("Authorization", "Bearer fbt_invalid").header("If-Match", tag)
                .contentType("application/json").content(body)).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("ETag"));
        mvc.perform(put(PATH + "/language").with(user(EMAIL)).with(csrf()).header("If-Match", tag)
                .contentType("application/json").content(body)).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("ETag"));
        jdbc.update("update app_token set expires_at = ? where owner_id = ?",
                java.sql.Timestamp.from(START.minusSeconds(1)), driver.getId());
        mvc.perform(put(PATH + "/language").header("Authorization", "Bearer " + token).header("If-Match", tag)
                .contentType("application/json").content(body)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForMap("select * from app_users where id = ?", driver.getId())).isEqualTo(row);
    }

    @Test
    void theTokenAloneChoosesTheAccount() throws Exception {
        other.setWeeklyGoal(new BigDecimal("999"));
        users.saveAndFlush(other);
        String otherTag = etag(read(otherToken));
        MvcResult own = mvc.perform(get(PATH).header("Authorization", "Bearer " + token)
                .param("email", OTHER).param("ownerId", other.getId().toString())).andExpect(status().isOk()).andReturn();
        assertThat(body(own).get("weeklyGoal").isNull()).isTrue();
        mvc.perform(put(PATH + "/goals").header("Authorization", "Bearer " + token).header("If-Match", etag(own))
                        .param("email", OTHER).param("ownerId", other.getId().toString())
                        .contentType("application/json")
                        .content("{\"weeklyGoal\":\"123.00\",\"goalBasis\":\"gross\",\"email\":\"" + OTHER
                                + "\",\"ownerId\":" + other.getId() + "}"))
                .andExpect(status().isOk());
        // Another account's tag never matches this account's settings.
        write("goals", "{\"weeklyGoal\":\"456.00\",\"goalBasis\":\"gross\"}", otherTag, token)
                .andExpect(status().isConflict());
        assertThat(users.findById(driver.getId()).orElseThrow().getWeeklyGoal()).isEqualByComparingTo("123");
        assertThat(users.findById(other.getId()).orElseThrow().getWeeklyGoal()).isEqualByComparingTo("999");
        assertThat(etag(read(otherToken))).isEqualTo(otherTag);
    }

    private ResultActions write(String group, String body, String tag, String bearer) throws Exception {
        return mvc.perform(put(PATH + "/" + group).header("Authorization", "Bearer " + bearer).header("If-Match", tag)
                .contentType("application/json").content(body));
    }

    private void invalid(String group, String body, String tag, String... fields) throws Exception {
        MvcResult result = write(group, body, tag, token).andExpect(status().isBadRequest())
                .andExpect(header().doesNotExist("ETag")).andReturn();
        JsonNode answer = body(result);
        assertThat(answer.get("error").asText()).as(body).isEqualTo("invalid");
        assertThat(answer.get("fields").propertyNames()).as(body).containsExactlyInAnyOrder(fields);
    }

    private JsonNode website() throws Exception {
        return body(mvc.perform(get("/account/settings").with(user(EMAIL))).andExpect(status().isOk()).andReturn());
    }

    private MvcResult read(String bearer) throws Exception {
        return mvc.perform(get(PATH).header("Authorization", "Bearer " + bearer))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(header().string("Cache-Control", "private, no-cache")).andReturn();
    }

    private String signIn(String email) throws Exception {
        return body(mvc.perform(post("/api/v1/auth/sign-in").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn()).get("token").asText();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String etag(MvcResult result) { return result.getResponse().getHeader("ETag"); }
}
