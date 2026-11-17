package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
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
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

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

import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.GoalBasis;
import com.angel.flexbuddy.model.VehicleCostMethod;
import com.angel.flexbuddy.repository.AppTokenRepository;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.security.MutableClock;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Real bearer authentication and synthetic accounts; no production data. */
@SpringBootTest(properties = {"flexbuddy.api.enabled=true", "flexbuddy.security.remember-me-key=api-test-key"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ApiCalculationSettingsIntegrationTest {
    private static final String PATH = "/api/v1/calculation-settings";
    private static final String EMAIL = "settings-driver@example.test";
    private static final String OTHER = "settings-other@example.test";
    private static final String PASSWORD = "settings-test-password";
    private static final Instant START = Instant.parse("2026-06-30T16:00:00Z");

    @TestConfiguration
    static class Beans {
        @Bean @Primary MutableClock settingsClock() { return new MutableClock(START); }
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
        clock.advance(Duration.between(clock.instant(), START));
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
    void defaultsAreExactlyTheDedicatedContractAndContainNoPrivateSettings() throws Exception {
        driver.setCalendarToken("synthetic-private-feed-marker");
        driver.setTaxSetAsidePercent(new BigDecimal("25"));
        driver.setRemindTax(true);
        users.saveAndFlush(driver);

        MvcResult result = read(token);
        JsonNode body = body(result);
        assertThat(body.propertyNames()).containsExactlyInAnyOrder("timeZone", "vehicleCostMethod", "customMileageRate",
                "weeklyGoal", "monthlyGoal", "goalBasis", "payoutDays", "payoutLagDays");
        assertThat(body.get("timeZone").asText()).isEqualTo(AppUser.DEFAULT_TIME_ZONE);
        assertThat(body.get("vehicleCostMethod").asText()).isEqualTo("standard_mileage");
        assertThat(body.get("customMileageRate").isNull()).isTrue();
        assertThat(body.get("weeklyGoal").isNull()).isTrue();
        assertThat(body.get("monthlyGoal").isNull()).isTrue();
        assertThat(body.get("goalBasis").asText()).isEqualTo("gross");
        assertThat(body.get("payoutDays")).isEqualTo(json.readTree("[\"tuesday\",\"friday\"]"));
        assertThat(body.get("payoutLagDays").asInt()).isEqualTo(AppUser.DEFAULT_PAYOUT_LAG_DAYS);
        assertThat(result.getResponse().getContentAsString()).doesNotContain("synthetic-private-feed-marker", "/calendar/", PASSWORD);
    }

    @Test
    void customRatePrecisionZeroAndBothMethodsAndGoalBasesArePreserved() throws Exception {
        driver.setMileageRate(new BigDecimal("0.655"));
        driver.setVehicleCostMethod(VehicleCostMethod.ACTUAL_EXPENSES);
        driver.setWeeklyGoal(new BigDecimal("600"));
        driver.setMonthlyGoal(new BigDecimal("2500.50"));
        driver.setGoalBasis(GoalBasis.NET);
        driver.setTimeZone("America/Chicago");
        driver.setPayoutDays("FRIDAY,MONDAY,WEDNESDAY");
        driver.setPayoutLagDays(2);
        users.saveAndFlush(driver);
        JsonNode body = body(read(token));
        assertThat(body.get("customMileageRate").isString()).isTrue();
        assertThat(body.get("customMileageRate").asText()).isEqualTo("0.655");
        assertThat(body.get("vehicleCostMethod").asText()).isEqualTo("actual_expenses");
        assertThat(body.get("weeklyGoal").asText()).isEqualTo("600.00");
        assertThat(body.get("monthlyGoal").asText()).isEqualTo("2500.50");
        assertThat(body.get("goalBasis").asText()).isEqualTo("net");
        assertThat(body.get("timeZone").asText()).isEqualTo("America/Chicago");
        assertThat(body.get("payoutDays")).isEqualTo(json.readTree("[\"monday\",\"wednesday\",\"friday\"]"));
        assertThat(body.get("payoutLagDays").asInt()).isEqualTo(2);

        driver.setMileageRate(BigDecimal.ZERO);
        users.saveAndFlush(driver);
        assertThat(new BigDecimal(body(read(token)).get("customMileageRate").asText())).isZero();
        driver.setMileageRate(null);
        users.saveAndFlush(driver);
        assertThat(body(read(token)).get("customMileageRate").isNull()).isTrue();
    }

    @Test
    void unparseablePayoutDaysUseTheSettingsServicesDefault() throws Exception {
        driver.setPayoutDays("bogus");
        users.saveAndFlush(driver);
        JsonNode body = body(read(token));
        assertThat(body.get("vehicleCostMethod").asText()).isEqualTo("standard_mileage");
        assertThat(body.get("goalBasis").asText()).isEqualTo("gross");
        assertThat(body.get("timeZone").asText()).isEqualTo(AppUser.DEFAULT_TIME_ZONE);
        assertThat(body.get("payoutDays")).isEqualTo(json.readTree("[\"tuesday\",\"friday\"]"));
    }

    @Test
    void theBearerAccountAloneSelectsSettingsAndConditionalResponses() throws Exception {
        other.setWeeklyGoal(new BigDecimal("999"));
        other.setMileageRate(new BigDecimal("0.800"));
        users.saveAndFlush(other);
        MvcResult own = mvc.perform(get(PATH).header("Authorization", "Bearer " + token)
                        .param("email", OTHER).param("ownerId", other.getId().toString()))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(own).get("weeklyGoal").isNull()).isTrue();
        MvcResult theirs = mvc.perform(get(PATH).header("Authorization", "Bearer " + otherToken)
                        .header("If-None-Match", etag(own)))
                .andExpect(status().isOk()).andReturn();
        assertThat(body(theirs).get("weeklyGoal").asText()).isEqualTo("999.00");
        assertThat(body(theirs).get("customMileageRate").asText()).isEqualTo("0.800");
        assertThat(etag(theirs)).isNotEqualTo(etag(own));
    }

    @Test
    void authenticationRunsBeforeConditionalGetEvenForBrowserSessions() throws Exception {
        String tag = etag(read(token));
        mvc.perform(get(PATH).header("If-None-Match", tag)).andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("ETag"));
        mvc.perform(get(PATH).header("Authorization", "Bearer fbt_invalid").header("If-None-Match", tag))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("ETag"));
        mvc.perform(get(PATH).with(user(EMAIL)).header("If-None-Match", tag))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("ETag"));
        jdbc.update("update app_token set expires_at = ? where owner_id = ?", java.sql.Timestamp.from(START.minusSeconds(1)), driver.getId());
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token).header("If-None-Match", tag))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("ETag"));
        tokens.deleteAll();
        mvc.perform(get(PATH).header("Authorization", "Bearer " + otherToken).header("If-None-Match", tag))
                .andExpect(status().isUnauthorized()).andExpect(header().doesNotExist("ETag"));
    }

    @Test
    void stableStrongEtagsReturnEmpty304AndNeverExposeSharedCaches() throws Exception {
        MvcResult first = read(token);
        assertThat(etag(first)).matches("\"[0-9a-f]{64}\"");
        assertThat(read(token).getResponse().getContentAsByteArray()).isEqualTo(first.getResponse().getContentAsByteArray());
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token).header("If-None-Match", etag(first)))
                .andExpect(status().isNotModified()).andExpect(content().string(""))
                .andExpect(header().string("ETag", etag(first)))
                .andExpect(header().string("Cache-Control", "private, no-cache"));
    }

    @Test
    void eachRepresentedSettingInvalidatesTheEtag() throws Exception {
        List<Consumer<AppUser>> changes = List.of(
                account -> account.setTimeZone("America/Chicago"),
                account -> account.setVehicleCostMethod(VehicleCostMethod.ACTUAL_EXPENSES),
                account -> account.setMileageRate(new BigDecimal("0.655")),
                account -> account.setWeeklyGoal(new BigDecimal("500")),
                account -> account.setMonthlyGoal(new BigDecimal("2000")),
                account -> account.setGoalBasis(GoalBasis.NET),
                account -> account.setPayoutDays("MONDAY,THURSDAY"),
                account -> account.setPayoutLagDays(3));
        String previous = etag(read(token));
        for (Consumer<AppUser> change : changes) {
            change.accept(driver);
            users.saveAndFlush(driver);
            MvcResult changed = mvc.perform(get(PATH).header("Authorization", "Bearer " + token).header("If-None-Match", previous))
                    .andExpect(status().isOk()).andReturn();
            assertThat(etag(changed)).isNotEqualTo(previous);
            previous = etag(changed);
        }
    }

    @Test
    void unrelatedPreferencesAndCrossingTheIrsRateBoundaryLeaveTheEtagUnchanged() throws Exception {
        String tag = etag(read(token));
        driver.setRemindTax(true);
        driver.setLanguage("es");
        driver.setTaxSetAsidePercent(new BigDecimal("25"));
        driver.setCalendarToken("another-synthetic-feed-marker");
        users.saveAndFlush(driver);
        clock.advance(Duration.ofDays(2));
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token).header("If-None-Match", tag))
                .andExpect(status().isNotModified()).andExpect(content().string(""));
    }

    @Test
    void payoutOrderingAndEnumCodesDoNotDependOnJvmLocaleOrStoredOrder() throws Exception {
        driver.setPayoutDays("FRIDAY,TUESDAY");
        users.saveAndFlush(driver);
        String tag = etag(read(token));
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr"));
            driver.setPayoutDays("TUESDAY,FRIDAY");
            users.saveAndFlush(driver);
            mvc.perform(get(PATH).header("Authorization", "Bearer " + token).header("If-None-Match", tag))
                    .andExpect(status().isNotModified());
        } finally {
            Locale.setDefault(original);
        }
    }

    @Test
    void readsDoNotPersistSettingsOrGenerateCalendarTokens() throws Exception {
        Map<String, Object> before = jdbc.queryForMap("select * from app_users where id = ?", driver.getId());
        MvcResult first = read(token);
        mvc.perform(get(PATH).header("Authorization", "Bearer " + token).header("If-None-Match", etag(first)))
                .andExpect(status().isNotModified());
        assertThat(jdbc.queryForMap("select * from app_users where id = ?", driver.getId())).isEqualTo(before);
        assertThat(users.findById(driver.getId()).orElseThrow().getCalendarToken()).isNull();
        mvc.perform(put(PATH).header("Authorization", "Bearer " + token).contentType("application/json").content("{}"))
                .andExpect(status().isMethodNotAllowed());
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
