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
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
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

import com.angel.flexbuddy.dto.StandingEntryRequest;
import com.angel.flexbuddy.dto.StandingEntryResponse;
import com.angel.flexbuddy.dto.StandingEventResponse;
import com.angel.flexbuddy.dto.StandingResponse;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.StandingLevel;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.security.MutableClock;
import com.angel.flexbuddy.service.StandingService;

import jakarta.persistence.EntityManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

@SpringBootTest(properties = "flexbuddy.api.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ApiStandingIntegrationTest {
    private static final String PATH = "/api/v1/standing";
    private static final String PASSWORD = "standing-example-password";
    private static final Instant START = Instant.parse("2026-11-21T07:59:59Z");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final String PRIVATE_CACHE = "private, no-cache";

    @TestConfiguration
    static class Beans {
        @Bean @Primary MutableClock standingClock() { return new MutableClock(START); }
    }

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired ShiftRepository shifts;
    @Autowired StandingService standing;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired EntityManager entityManager;

    private AppUser driver;
    private String token;

    @BeforeEach
    void setUp() throws Exception {
        clock.advance(Duration.between(clock.instant(), START));
        driver = account("standing-driver@example.test", "America/Los_Angeles");
        token = signIn(driver);
    }

    @Test
    void standingWindowMatchesWebsiteWithCarryInEventsAndWireConventions() throws Exception {
        logEntry(driver, LocalDate.of(2026, 4, 15), StandingLevel.AT_RISK, "Superseded carry-in");
        logEntry(driver, LocalDate.of(2026, 6, 1), StandingLevel.FANTASTIC, null);
        logEntry(driver, LocalDate.of(2026, 9, 10), StandingLevel.GREAT, "   ");
        logEntry(driver, LocalDate.of(2026, 10, 14), StandingLevel.FAIR, "  After late forfeit  ");
        logEntry(driver, LocalDate.of(2026, 11, 12), StandingLevel.AT_RISK, "Late warning");

        block(driver, "OLD1", LocalDate.of(2026, 8, 22), LocalTime.of(8, 0), ShiftStatus.FORFEITED, true);
        block(driver, "DXX1", LocalDate.of(2026, 10, 10), LocalTime.of(9, 5, 30), ShiftStatus.FORFEITED, true);
        block(driver, "VEA7", LocalDate.of(2026, 10, 10), LocalTime.of(14, 30), ShiftStatus.FORFEITED, false);
        block(driver, "BDL4", LocalDate.of(2026, 11, 2), LocalTime.of(18, 0), ShiftStatus.CANCELLED, false);
        block(driver, "WORK1", LocalDate.of(2026, 11, 5), LocalTime.of(10, 0), ShiftStatus.COMPLETED, false);
        block(driver, "NEXT1", LocalDate.of(2026, 11, 20), LocalTime.of(12, 0), ShiftStatus.SCHEDULED, false);
        Shift deleted = block(driver, "DEL1", LocalDate.of(2026, 11, 8), LocalTime.of(7, 15), ShiftStatus.FORFEITED, false);
        softDelete(driver, deleted);

        JsonNode api = body(read(request(token)));
        assertParity(api, website(driver, 90));
        assertThat(api.get("from").asText()).isEqualTo("2026-08-23");
        assertThat(api.get("to").asText()).isEqualTo("2026-11-20");
        assertThat(api.get("current").get("recordedOn").asText()).isEqualTo("2026-11-12");
        assertThat(api.get("current").get("level").asText()).isEqualTo("at_risk");
        assertThat(api.get("current").get("note").asText()).isEqualTo("Late warning");

        assertThat(api.get("entries")).hasSize(4);
        assertThat(api.get("entries").get(0).get("recordedOn").asText()).isEqualTo("2026-06-01");
        assertThat(api.get("entries").get(0).get("level").asText()).isEqualTo("fantastic");
        assertThat(api.get("entries").get(0).get("note").isNull()).isTrue();
        assertThat(api.get("entries").get(1).get("level").asText()).isEqualTo("great");
        assertThat(api.get("entries").get(1).get("note").isNull()).isTrue();
        assertThat(api.get("entries").get(2).get("level").asText()).isEqualTo("fair");
        assertThat(api.get("entries").get(2).get("note").asText()).isEqualTo("After late forfeit");
        assertThat(api.get("entries").get(3).get("level").asText()).isEqualTo("at_risk");

        assertThat(api.get("events")).hasSize(3);
        assertThat(api.get("events").get(0).get("startTime").asText()).isEqualTo("09:05");
        assertThat(api.get("events").get(0).get("station").asText()).isEqualTo("DXX1");
        assertThat(api.get("events").get(0).get("kind").asText()).isEqualTo("late_forfeit");
        assertThat(api.get("events").get(1).get("startTime").asText()).isEqualTo("14:30");
        assertThat(api.get("events").get(1).get("station").asText()).isEqualTo("VEA7");
        assertThat(api.get("events").get(1).get("kind").asText()).isEqualTo("forfeited");
        assertThat(api.get("events").get(2).get("startTime").asText()).isEqualTo("18:00");
        assertThat(api.get("events").get(2).get("station").asText()).isEqualTo("BDL4");
        assertThat(api.get("events").get(2).get("kind").asText()).isEqualTo("cancelled");
    }

    @Test
    void emptyStandingMatchesWebsiteWithNullCurrentAndEmptyLists() throws Exception {
        JsonNode api = body(read(request(token)));
        assertParity(api, website(driver, 90));
        assertThat(api.get("from").asText()).isEqualTo("2026-08-23");
        assertThat(api.get("to").asText()).isEqualTo("2026-11-20");
        assertThat(api.get("current").isNull()).isTrue();
        assertThat(api.get("entries")).isEmpty();
        assertThat(api.get("events")).isEmpty();
    }

    @Test
    void carryInEntryOlderThanWindowIsStillCurrentAndFirstEntry() throws Exception {
        logEntry(driver, LocalDate.of(2026, 5, 1), StandingLevel.GREAT, null);

        JsonNode api = body(read(request(token).param("days", "30")));
        assertParity(api, website(driver, 30));
        assertThat(api.get("from").asText()).isEqualTo("2026-10-22");
        assertThat(api.get("to").asText()).isEqualTo("2026-11-20");
        assertThat(api.get("current").get("recordedOn").asText()).isEqualTo("2026-05-01");
        assertThat(api.get("current").get("level").asText()).isEqualTo("great");
        assertThat(api.get("current").get("note").isNull()).isTrue();
        assertThat(api.get("entries")).hasSize(1);
        assertThat(api.get("entries").get(0)).isEqualTo(api.get("current"));
    }

    @Test
    void windowFollowsAccountTimeZoneAcrossLocalMidnight() throws Exception {
        AppUser eastern = account("standing-east@example.test", "America/New_York");
        String easternToken = signIn(eastern);
        logEntry(eastern, LocalDate.of(2026, 11, 21), StandingLevel.FANTASTIC, null);

        MvcResult before = read(request(token));
        assertThat(body(before).get("from").asText()).isEqualTo("2026-08-23");
        assertThat(body(before).get("to").asText()).isEqualTo("2026-11-20");

        JsonNode eastBody = body(read(request(easternToken)));
        assertParity(eastBody, website(eastern, 90));
        assertThat(eastBody.get("from").asText()).isEqualTo("2026-08-24");
        assertThat(eastBody.get("to").asText()).isEqualTo("2026-11-21");
        assertThat(eastBody.get("current").get("recordedOn").asText()).isEqualTo("2026-11-21");

        clock.advance(Duration.ofSeconds(1));
        MvcResult after = read(request(token).header("If-None-Match", etag(before)));
        assertThat(body(after).get("from").asText()).isEqualTo("2026-08-24");
        assertThat(body(after).get("to").asText()).isEqualTo("2026-11-21");
        assertThat(etag(after)).isNotEqualTo(etag(before));
    }

    @Test
    void supportedDaysBoundsAreInclusive() throws Exception {
        JsonNode min = body(read(request(token).param("days", "7")));
        assertParity(min, website(driver, 7));
        assertThat(min.get("from").asText()).isEqualTo("2026-11-14");
        assertThat(min.get("to").asText()).isEqualTo("2026-11-20");

        JsonNode max = body(read(request(token).param("days", "365")));
        assertParity(max, website(driver, 365));
        assertThat(max.get("from").asText()).isEqualTo("2025-11-21");
        assertThat(max.get("to").asText()).isEqualTo("2026-11-20");
    }

    @ParameterizedTest
    @ValueSource(strings = {"6", "366", "0", "-7", "90.5", "0x5a", "2147483648", "bogus", "", " "})
    void invalidDaysGetsTheApiFieldError(String days) throws Exception {
        mvc.perform(request(token).param("days", days))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"invalid\",\"fields\":{\"days\":\"invalid\"}}"))
                .andExpect(header().doesNotExist("ETag"));
    }

    @Test
    void duplicateDaysParameterGetsTheApiFieldError() throws Exception {
        mvc.perform(request(token).param("days", "30").param("days", "60"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().json("{\"error\":\"invalid\",\"fields\":{\"days\":\"invalid\"}}"))
                .andExpect(header().doesNotExist("ETag"));
    }

    @Test
    void validatorsAndAccountSelectorsCannotReadAnotherAccountsStanding() throws Exception {
        AppUser other = account("standing-other@example.test", "America/Los_Angeles");
        String otherToken = signIn(other);
        logEntry(driver, LocalDate.of(2026, 11, 10), StandingLevel.FANTASTIC, "Driver note");
        logEntry(other, LocalDate.of(2026, 11, 11), StandingLevel.FAIR, "Other note");
        block(driver, "DRV1", LocalDate.of(2026, 11, 10), LocalTime.of(8, 0), ShiftStatus.FORFEITED, false);
        block(other, "OTH1", LocalDate.of(2026, 11, 11), LocalTime.of(16, 0), ShiftStatus.CANCELLED, false);

        MvcResult first = read(request(token));
        MvcResult second = read(request(otherToken).header("If-None-Match", etag(first))
                .param("email", driver.getEmail()).param("ownerId", driver.getId().toString()));
        assertParity(body(first), website(driver, 90));
        assertParity(body(second), website(other, 90));
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
        mvc.perform(get("/standing").session(session)).andExpect(status().isOk());

        for (var attempt : new MockHttpServletRequestBuilder[]{
                get(PATH), request("fbt_" + "a".repeat(43)), get(PATH).session(session)}) {
            mvc.perform(attempt.header("If-None-Match", tag))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json("{\"error\":\"unauthorized\"}"))
                    .andExpect(header().doesNotExist("ETag"));
        }
    }

    @Test
    void unchangedDataRevalidatesAndEntryOrBlockChangesInvalidateEtag() throws Exception {
        String initialTag = etag(read(request(token)));
        assertThat(initialTag).matches("\"[0-9a-f]{64}\"");
        mvc.perform(request(token).header("If-None-Match", initialTag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", initialTag))
                .andExpect(header().string("Cache-Control", PRIVATE_CACHE))
                .andExpect(content().string(""));

        logEntry(driver, LocalDate.of(2026, 11, 15), StandingLevel.GREAT, "Logged entry");
        MvcResult afterLog = read(request(token).header("If-None-Match", initialTag));
        String afterLogTag = etag(afterLog);
        assertThat(afterLogTag).isNotEqualTo(initialTag);
        assertThat(body(afterLog).get("current").get("level").asText()).isEqualTo("great");
        assertThat(body(afterLog).get("entries")).hasSize(1);

        mvc.perform(request(token).header("If-None-Match", afterLogTag))
                .andExpect(status().isNotModified())
                .andExpect(header().string("ETag", afterLogTag))
                .andExpect(header().string("Cache-Control", PRIVATE_CACHE))
                .andExpect(content().string(""));

        Shift forfeit = block(driver, "VEA7", LocalDate.of(2026, 11, 16), LocalTime.of(10, 15),
                ShiftStatus.FORFEITED, false);
        MvcResult afterEvent = read(request(token).header("If-None-Match", afterLogTag));
        String afterEventTag = etag(afterEvent);
        assertThat(afterEventTag).isNotEqualTo(afterLogTag);
        assertThat(body(afterEvent).get("events")).hasSize(1);
        assertThat(body(afterEvent).get("events").get(0).get("kind").asText()).isEqualTo("forfeited");

        forfeit.setLateForfeit(true);
        shifts.saveAndFlush(forfeit);
        MvcResult afterLateUpdate = read(request(token).header("If-None-Match", afterEventTag));
        String afterLateTag = etag(afterLateUpdate);
        assertThat(afterLateTag).isNotEqualTo(afterEventTag);
        assertThat(body(afterLateUpdate).get("events").get(0).get("kind").asText()).isEqualTo("late_forfeit");

        standing.remove(driver.getEmail(), LocalDate.of(2026, 11, 15));
        MvcResult afterRemove = read(request(token).header("If-None-Match", afterLateTag));
        String afterRemoveTag = etag(afterRemove);
        assertThat(afterRemoveTag).isNotEqualTo(afterLateTag);
        assertThat(body(afterRemove).get("current").isNull()).isTrue();
        assertThat(body(afterRemove).get("entries")).isEmpty();

        softDelete(driver, forfeit);
        MvcResult afterDeleteEvent = read(request(token).header("If-None-Match", afterRemoveTag));
        assertThat(etag(afterDeleteEvent)).isNotEqualTo(afterRemoveTag);
        assertThat(body(afterDeleteEvent).get("events")).isEmpty();
    }

    private AppUser account(String email, String zone) {
        AppUser user = new AppUser("Standing example driver", email, encoder.encode(PASSWORD));
        user.setTimeZone(zone);
        return users.saveAndFlush(user);
    }

    private void logEntry(AppUser owner, LocalDate date, StandingLevel level, String note) {
        standing.log(owner.getEmail(), date, new StandingEntryRequest(level, note));
    }

    private Shift block(AppUser owner, String station, LocalDate date, LocalTime start, ShiftStatus status,
            boolean lateForfeit) {
        Shift shift = new Shift(null, station, date, start, start.plusHours(3),
                new BigDecimal("72.00"), BigDecimal.ZERO, owner);
        shift.setStatus(status);
        shift.setLateForfeit(lateForfeit);
        return shifts.saveAndFlush(shift);
    }

    private void softDelete(AppUser owner, Shift shift) {
        entityManager.flush();
        shifts.softDelete(owner.getEmail(), shift.getId(), clock.instant(), "test-batch");
        entityManager.clear();
    }

    private String signIn(AppUser owner) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("email", owner.getEmail(), "password", PASSWORD))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("signed_in")).andReturn();
        return body(result).get("token").asText();
    }

    private StandingResponse website(AppUser owner, int days) throws Exception {
        MvcResult result = mvc.perform(get("/standing").with(user(owner.getEmail()))
                        .param("days", String.valueOf(days)))
                .andExpect(status().isOk()).andReturn();
        return json.readValue(result.getResponse().getContentAsByteArray(), StandingResponse.class);
    }

    private void assertParity(JsonNode api, StandingResponse web) {
        assertThat(api.propertyNames()).containsExactlyInAnyOrder("from", "to", "current", "entries", "events");
        assertThat(api.get("from")).isEqualTo(json.valueToTree(web.from().toString()));
        assertThat(api.get("to")).isEqualTo(json.valueToTree(web.to().toString()));
        assertEntry(api.get("current"), web.current());

        JsonNode entries = api.get("entries");
        assertThat(entries.isArray()).isTrue();
        assertThat(entries).hasSize(web.entries().size());
        for (int index = 0; index < entries.size(); index++) {
            assertEntry(entries.get(index), web.entries().get(index));
        }

        JsonNode events = api.get("events");
        assertThat(events.isArray()).isTrue();
        assertThat(events).hasSize(web.events().size());
        for (int index = 0; index < events.size(); index++) {
            JsonNode actual = events.get(index);
            StandingEventResponse expected = web.events().get(index);
            assertThat(actual.propertyNames()).containsExactlyInAnyOrder("date", "startTime", "station", "kind");
            assertThat(actual.get("date")).isEqualTo(json.valueToTree(expected.date().toString()));
            assertThat(actual.get("startTime")).isEqualTo(json.valueToTree(expected.startTime().format(TIME)));
            assertThat(actual.get("station")).isEqualTo(json.valueToTree(expected.station()));
            assertThat(actual.get("kind")).isEqualTo(json.valueToTree(expected.kind().name().toLowerCase(Locale.ROOT)));
        }
    }

    private void assertEntry(JsonNode actual, StandingEntryResponse expected) {
        if (expected == null) {
            assertThat(actual.isNull()).isTrue();
            return;
        }
        assertThat(actual.propertyNames()).containsExactlyInAnyOrder("recordedOn", "level", "note");
        assertThat(actual.get("recordedOn")).isEqualTo(json.valueToTree(expected.recordedOn().toString()));
        assertThat(actual.get("level")).isEqualTo(json.valueToTree(expected.level().name().toLowerCase(Locale.ROOT)));
        assertThat(actual.get("note")).isEqualTo(json.valueToTree(expected.note()));
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
