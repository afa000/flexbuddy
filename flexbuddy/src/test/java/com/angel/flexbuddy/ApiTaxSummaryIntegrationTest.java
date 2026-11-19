package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import com.angel.flexbuddy.dto.ExpenseRequest;
import com.angel.flexbuddy.dto.TaxPaymentRequest;
import com.angel.flexbuddy.dto.TaxQuarterResponse;
import com.angel.flexbuddy.dto.TaxSummaryResponse;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.ExpenseCategory;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.security.MutableClock;
import com.angel.flexbuddy.service.ExpenseService;
import com.angel.flexbuddy.service.TaxService;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "flexbuddy.api.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiTaxSummaryIntegrationTest {
    private static final String PATH = "/api/v1/tax/summary";
    private static final String PASSWORD = "tax-example-password";
    private static final Instant START = Instant.parse("2027-01-01T07:59:59Z");
    private static final LocalDate WEEK_DATE = LocalDate.of(2026, 12, 29);
    private static final String PRIVATE_CACHE = "private, no-cache";

    @TestConfiguration
    static class Beans {
        @Bean @Primary MutableClock taxClock() { return new MutableClock(START); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired ShiftRepository shifts;
    @Autowired ExpenseService expenses;
    @Autowired TaxService tax;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;

    private AppUser driver;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        clock.advance(Duration.between(clock.instant(), START));
        driver = account("tax-driver@example.test", "America/Los_Angeles", "25.50");
        token = signIn(driver);
    }

    @Test
    void figuresMatchTheWebsiteAndOnlyIncludeAnnualAndQuarterlyTotals() throws Exception {
        block(driver, LocalDate.of(2026, 2, 2), "300", ShiftStatus.COMPLETED);
        block(driver, WEEK_DATE, "120.50", ShiftStatus.COMPLETED);
        block(driver, WEEK_DATE, "70", ShiftStatus.SCHEDULED);
        expenses.create(driver.getEmail(), new ExpenseRequest(WEEK_DATE, ExpenseCategory.TOLL,
                new BigDecimal("10.25"), null, null));
        payment(driver, 1, "20.25");
        payment(driver, null, "5");

        JsonNode summary = body(read(request(token).param("year", "2026")));
        assertParity(summary, website(driver, 2026));
        assertThat(summary.get("netYearToDate").asText()).isEqualTo("410.25");
        assertThat(summary.get("reserveToDate").asText()).isEqualTo("104.61");
        assertThat(summary.get("paid").asText()).isEqualTo("25.25");
        assertThat(summary.get("remaining").asText()).isEqualTo("79.36");
        assertThat(summary.get("thisWeekNet").asText()).isEqualTo("110.25");
        assertThat(summary.get("thisWeekSetAside").asText()).isEqualTo("28.11");
        assertThat(summary.get("nextDueDate").asText()).isEqualTo("2027-01-15");
        assertThat(summary.get("quarters").get(0).get("paid").asText()).isEqualTo("20.25");
    }

    @Test
    void savedZeroUnsetAndPastDueDatesMatchTheWebsite() throws Exception {
        block(driver, WEEK_DATE, "120", ShiftStatus.COMPLETED);
        driver.setTaxSetAsidePercent(BigDecimal.ZERO);
        users.saveAndFlush(driver);
        JsonNode zero = body(read(request(token).param("year", "2026")));
        assertParity(zero, website(driver, 2026));
        assertThat(new BigDecimal(zero.get("percent").asText())).isZero();
        assertThat(zero.get("reserveToDate").asText()).isEqualTo("0.00");

        driver.setTaxSetAsidePercent(null);
        users.saveAndFlush(driver);
        block(driver, LocalDate.of(2025, 5, 1), "186", ShiftStatus.COMPLETED);
        JsonNode historical = body(read(request(token).param("year", "2025")));
        assertParity(historical, website(driver, 2025));
        assertThat(historical.get("netYearToDate").asText()).isEqualTo("186.00");
        assertThat(historical.get("thisWeekNet").asText()).isEqualTo("120.00");
        for (String field : new String[]{"percent", "reserveToDate", "remaining", "thisWeekSetAside", "nextDueDate"}) {
            assertThat(historical.get(field).isNull()).as(field).isTrue();
        }
        historical.get("quarters").forEach(quarter -> assertThat(quarter.get("setAside").isNull()).isTrue());
    }

    @Test
    void omittedYearFollowsTheAccountAcrossLocalMidnight() throws Exception {
        AppUser eastern = account("tax-east@example.test", "America/New_York", null);
        String easternToken = signIn(eastern);
        MvcResult before = read(request(token));
        assertThat(body(before).get("year").asInt()).isEqualTo(2026);
        assertThat(body(read(request(easternToken))).get("year").asInt()).isEqualTo(2027);

        clock.advance(Duration.ofSeconds(1));
        MvcResult after = read(request(token).header("If-None-Match", etag(before)));
        assertThat(body(after).get("year").asInt()).isEqualTo(2027);
        assertThat(etag(after)).isNotEqualTo(etag(before));
    }

    @Test
    void supportedYearBoundsAreInclusive() throws Exception {
        read(request(token).param("year", "2000"));
        read(request(token).param("year", "2100"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"1999", "2101", "2026.5", "0x7ea", "2147483648", "bogus", "", " "})
    void invalidYearGetsTheApiFieldError(String year) throws Exception {
        mvc.perform(request(token).param("year", year))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"invalid\",\"fields\":{\"year\":\"invalid\"}}"))
                .andExpect(header().doesNotExist("ETag"));
    }

    @Test
    void validatorsAndAccountSelectorsCannotReadAnotherAccountsFigures() throws Exception {
        AppUser other = account("tax-other@example.test", "America/Los_Angeles", "10");
        String otherToken = signIn(other);
        block(driver, WEEK_DATE, "120", ShiftStatus.COMPLETED);
        block(other, WEEK_DATE, "300", ShiftStatus.COMPLETED);
        payment(driver, 1, "20");
        payment(other, 1, "8");

        MvcResult first = read(request(token));
        MvcResult second = read(request(otherToken).header("If-None-Match", etag(first))
                .param("email", driver.getEmail()).param("ownerId", driver.getId().toString()));
        assertParity(body(first), website(driver, 2026));
        assertParity(body(second), website(other, 2026));
        assertThat(etag(second)).isNotEqualTo(etag(first));
    }

    @Test
    void revalidationRequiresBearerAuthenticationEvenWithAWebsiteSession() throws Exception {
        String tag = etag(read(request(token)));
        MvcResult login = mvc.perform(post("/login").with(csrf()).secure(true)
                        .param("username", driver.getEmail()).param("password", PASSWORD))
                .andExpect(status().is3xxRedirection()).andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        assertThat(session).isNotNull();
        mvc.perform(get("/tax/summary").session(session)).andExpect(status().isOk());

        for (var attempt : new MockHttpServletRequestBuilder[]{
                get(PATH), request("fbt_" + "a".repeat(43)), get(PATH).session(session)}) {
            mvc.perform(attempt.header("If-None-Match", tag))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json("{\"error\":\"unauthorized\"}"))
                    .andExpect(header().doesNotExist("ETag"));
        }
    }

    @Test
    void unchangedDataRevalidatesAndAPaymentChangesTheRepresentation() throws Exception {
        String tag = etag(read(request(token)));
        assertThat(tag).matches("\"[0-9a-f]{64}\"");
        mvc.perform(request(token).header("If-None-Match", tag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", tag))
                .andExpect(header().string("Cache-Control", PRIVATE_CACHE))
                .andExpect(content().string(""));

        payment(driver, 1, "8.50");
        MvcResult changed = read(request(token).header("If-None-Match", tag));
        assertThat(etag(changed)).isNotEqualTo(tag);
        assertThat(body(changed).get("paid").asText()).isEqualTo("8.50");
        assertThat(body(changed).get("quarters").get(0).get("paid").asText()).isEqualTo("8.50");
    }

    private AppUser account(String email, String zone, String percent) {
        AppUser user = new AppUser("Tax example driver", email, encoder.encode(PASSWORD));
        user.setTimeZone(zone);
        user.setTaxSetAsidePercent(percent == null ? null : new BigDecimal(percent));
        return users.saveAndFlush(user);
    }

    private void block(AppUser owner, LocalDate date, String pay, ShiftStatus status) {
        Shift shift = new Shift(null, "TEST", date, LocalTime.of(10, 30), LocalTime.of(13, 30),
                new BigDecimal(pay), BigDecimal.ZERO, owner);
        shift.setStatus(status);
        shifts.saveAndFlush(shift);
    }

    private void payment(AppUser owner, Integer quarter, String amount) {
        tax.addPayment(owner.getEmail(),
                new TaxPaymentRequest(2026, quarter, WEEK_DATE, new BigDecimal(amount), "Synthetic private payment note"));
    }

    private String signIn(AppUser owner) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", owner.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("signed_in")).andReturn();
        return body(result).get("token").asText();
    }

    private TaxSummaryResponse website(AppUser owner, int year) throws Exception {
        MvcResult result = mvc.perform(get("/tax/summary").with(user(owner.getEmail())).param("year", String.valueOf(year)))
                .andExpect(status().isOk()).andReturn();
        return json.readValue(result.getResponse().getContentAsByteArray(), TaxSummaryResponse.class);
    }

    private void assertParity(JsonNode api, TaxSummaryResponse web) {
        assertThat(api.propertyNames()).containsExactlyInAnyOrder("year", "percent", "netYearToDate", "reserveToDate",
                "paid", "remaining", "thisWeekNet", "thisWeekSetAside", "nextDueDate", "quarters");
        assertThat(api.get("year").isInt()).isTrue();
        assertThat(api.get("year").asInt()).isEqualTo(web.year());
        assertThat(api.get("percent")).isEqualTo(json.valueToTree(web.percent() == null ? null : web.percent().toPlainString()));
        assertMoney(api, "netYearToDate", web.netYearToDate());
        assertMoney(api, "reserveToDate", web.reserveToDate());
        assertMoney(api, "paid", web.paid());
        assertMoney(api, "remaining", web.remaining());
        assertMoney(api, "thisWeekNet", web.thisWeekNet());
        assertMoney(api, "thisWeekSetAside", web.thisWeekSetAside());
        assertThat(api.get("nextDueDate")).isEqualTo(json.valueToTree(web.nextDueDate() == null ? null : web.nextDueDate().toString()));

        JsonNode quarters = api.get("quarters");
        assertThat(quarters.isArray()).isTrue();
        assertThat(quarters.size()).isEqualTo(4);
        for (int index = 0; index < quarters.size(); index++) {
            JsonNode actual = quarters.get(index);
            TaxQuarterResponse expected = web.quarters().get(index);
            assertThat(actual.propertyNames()).containsExactlyInAnyOrder("quarter", "from", "to", "dueDate", "net", "setAside", "paid");
            assertThat(actual.get("quarter").isInt()).isTrue();
            assertThat(actual.get("quarter").asInt()).isEqualTo(index + 1).isEqualTo(expected.quarter());
            assertThat(actual.get("from")).isEqualTo(json.valueToTree(expected.from().toString()));
            assertThat(actual.get("to")).isEqualTo(json.valueToTree(expected.to().toString()));
            assertThat(actual.get("dueDate")).isEqualTo(json.valueToTree(expected.dueDate().toString()));
            assertMoney(actual, "net", expected.net());
            assertMoney(actual, "setAside", expected.setAside());
            assertMoney(actual, "paid", expected.paid());
        }
    }

    private void assertMoney(JsonNode object, String field, BigDecimal expected) {
        String money = expected == null ? null : expected.setScale(2, RoundingMode.UNNECESSARY).toPlainString();
        assertThat(object.get(field)).as(field).isEqualTo(json.valueToTree(money));
    }

    private static MockHttpServletRequestBuilder request(String bearer) {
        return get(PATH).header("Authorization", "Bearer " + bearer);
    }

    private MvcResult read(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(header().string("Cache-Control", PRIVATE_CACHE)).andReturn();
    }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsByteArray());
    }

    private static String etag(MvcResult result) {
        return result.getResponse().getHeader("ETag");
    }
}
