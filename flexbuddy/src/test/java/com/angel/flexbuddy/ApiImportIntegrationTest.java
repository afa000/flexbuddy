package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder;

import com.angel.flexbuddy.exception.ScreenshotBusyException;
import com.angel.flexbuddy.exception.ScreenshotOcrException;
import com.angel.flexbuddy.model.AppUser;
import com.angel.flexbuddy.model.Shift;
import com.angel.flexbuddy.model.ShiftStatus;
import com.angel.flexbuddy.model.TimestampListener;
import com.angel.flexbuddy.repository.ApiWriteReceiptRepository;
import com.angel.flexbuddy.repository.AppTokenRepository;
import com.angel.flexbuddy.repository.AppUserRepository;
import com.angel.flexbuddy.repository.ExpenseRepository;
import com.angel.flexbuddy.repository.ShiftRepository;
import com.angel.flexbuddy.security.MutableClock;
import com.angel.flexbuddy.service.OcrLine;
import com.angel.flexbuddy.service.OcrResult;
import com.angel.flexbuddy.service.ScreenshotTextExtractor;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * NA-07: screenshot import preview over the API. Tesseract is replaced by a fake returning made-up lines, and the
 * images are tiny generated PNGs, never a real driver's screenshot.
 */
@SpringBootTest(properties = {"flexbuddy.api.enabled=true", "flexbuddy.security.remember-me-key=api-test-key",
        "flexbuddy.import.ocr-wait=5s"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class ApiImportIntegrationTest {
    private static final String EMAIL = "import-driver@example.test";
    private static final String OTHER = "import-other@example.test";
    private static final String PASSWORD = "import-test-password";
    private static final String FILE_NAME = "made-up-private-file-name.png";
    /** Friday, November 13, 2026, 10:00 in New York. */
    private static final Instant START = Instant.parse("2026-11-13T15:00:00Z");
    /** A made-up block on Thursday 11/12, which has already happened at START. */
    private static final List<OcrLine> LINES = List.of(
            line("Windsor (DCY1)", 95, 0),
            line("Thursday, 11/12", 93, 1),
            line("04:00 - 07:30", 72, 2),
            line("Total $90.50", 95, 3),
            line("Base $72.00", 90, 4),
            line("Tips $18.50", 85, 5),
            line("Made-up private note 4417", 80, 6));
    private static final Set<String> TOP_KEYS = Set.of("meanConfidence", "lines", "rawText", "shifts");
    private static final Set<String> SHIFT_KEYS = Set.of("station", "date", "startTime", "endTime", "basePay", "tips",
            "suggestedStatus", "warnings", "duplicates");
    private static final Set<String> FIELD_KEYS = Set.of("value", "level", "confidence", "lineIndex");

    @TestConfiguration
    static class Beans {
        @Bean @Primary MutableClock importClock() { return new MutableClock(START); }
    }

    @MockitoBean ScreenshotTextExtractor extractor;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AppUserRepository users;
    @Autowired AppTokenRepository tokens;
    @Autowired ShiftRepository shifts;
    @Autowired ExpenseRepository expenses;
    @Autowired ApiWriteReceiptRepository receipts;
    @Autowired PasswordEncoder encoder;
    @Autowired MutableClock clock;
    @Autowired org.springframework.jdbc.core.JdbcTemplate jdbc;

    private AppUser driver;
    private AppUser other;
    private String token;
    private String otherToken;

    @BeforeEach
    void before() throws Exception {
        wipe();
        clock.advance(Duration.between(clock.instant(), START));
        new TimestampListener(clock);
        driver = account("Import Driver", EMAIL);
        other = account("Import Other", OTHER);
        token = signIn(EMAIL);
        otherToken = signIn(OTHER);
        reset(extractor);
        doReturn(OcrResult.fromLines(LINES)).when(extractor).extract(any());
    }

    @AfterEach
    void wipe() {
        receipts.deleteAll();
        tokens.deleteAll();
        expenses.deleteAll();
        jdbc.update("delete from shift");
        users.deleteAll();
    }

    @Test
    void previewNeedsATokenAndNeverRunsOcrWithoutOne() throws Exception {
        mvc.perform(multipart("/api/v1/shifts/import-preview").file(png()).header("Idempotency-Key", key()))
                .andExpect(status().isUnauthorized());
        // A browser session never authenticates the API.
        mvc.perform(multipart("/api/v1/shifts/import-preview").file(png()).header("Idempotency-Key", key())
                .with(user(EMAIL))).andExpect(status().isUnauthorized());
        verify(extractor, never()).extract(any());
    }

    @Test
    void previewReturnsWhatTheWebsiteReviewShowsAndNothingElse() throws Exception {
        MvcResult result = preview(token, key(), png(), 200);
        assertThat(result.getResponse().getHeader("Cache-Control")).contains("no-store");
        JsonNode api = body(result);
        JsonNode site = body(mvc.perform(multipart("/shifts/import-preview").file(png())
                .with(user(EMAIL)).with(csrf())).andExpect(status().isOk()).andReturn());

        assertThat(names(api)).isEqualTo(TOP_KEYS);
        assertThat(api.get("meanConfidence").asInt()).isEqualTo(site.get("meanConfidence").asInt());
        assertThat(api.get("rawText").isNull()).isTrue();
        assertThat(api.get("lines")).hasSize(site.get("lines").size());
        for (int i = 0; i < api.get("lines").size(); i++) {
            JsonNode line = api.get("lines").get(i);
            assertThat(names(line)).isEqualTo(Set.of("index", "text"));
            assertThat(line.get("index").asInt()).isEqualTo(site.get("lines").get(i).get("index").asInt());
            assertThat(line.get("text").asText()).isEqualTo(site.get("lines").get(i).get("text").asText());
        }

        JsonNode shift = api.get("shifts").get(0);
        JsonNode siteShift = site.get("shifts").get(0);
        assertThat(names(shift)).isEqualTo(SHIFT_KEYS);
        for (String name : List.of("station", "date", "startTime", "endTime", "basePay", "tips")) {
            JsonNode field = shift.get(name);
            JsonNode siteField = siteShift.get(name);
            assertThat(names(field)).isEqualTo(FIELD_KEYS);
            assertThat(field.get("level").asText()).isEqualTo(siteField.get("level").asText().toLowerCase());
            assertThat(field.get("confidence").isNull()).isEqualTo(siteField.get("confidence").isNull());
            assertThat(field.get("confidence").asInt()).isEqualTo(siteField.get("confidence").asInt());
            assertThat(field.get("lineIndex").isNull()).isEqualTo(siteField.get("lineIndex").isNull());
            assertThat(field.get("lineIndex").asInt()).isEqualTo(siteField.get("lineIndex").asInt());
        }
        assertThat(shift.get("station").get("value").asText()).isEqualTo(siteShift.get("station").get("value").asText())
                .isEqualTo("DCY1");
        assertThat(shift.get("date").get("value").asText()).isEqualTo(siteShift.get("date").get("value").asText())
                .isEqualTo("2026-11-12");
        assertThat(shift.get("startTime").get("value").asText()).isEqualTo("04:00");
        assertThat(LocalTime.parse(siteShift.get("startTime").get("value").asText())).isEqualTo(LocalTime.of(4, 0));
        assertThat(shift.get("endTime").get("value").asText()).isEqualTo("07:30");
        assertThat(shift.get("basePay").get("value").asText()).isEqualTo("72.00");
        assertThat(new BigDecimal(siteShift.get("basePay").get("value").asText())).isEqualByComparingTo("72.00");
        assertThat(shift.get("tips").get("value").asText()).isEqualTo("18.50");
        assertThat(new BigDecimal(siteShift.get("tips").get("value").asText())).isEqualByComparingTo("18.50");
        assertThat(shift.get("suggestedStatus").asText())
                .isEqualTo(siteShift.get("suggestedStatus").asText().toLowerCase()).isEqualTo("completed");

        assertThat(shift.get("warnings")).hasSize(siteShift.get("warnings").size());
        assertThat(shift.get("warnings")).isNotEmpty();
        for (int i = 0; i < shift.get("warnings").size(); i++) {
            JsonNode warning = shift.get("warnings").get(i);
            JsonNode siteWarning = siteShift.get("warnings").get(i);
            assertThat(names(warning)).isEqualTo(Set.of("code", "severity", "field"));
            assertThat(warning.get("code").asText()).isEqualTo(siteWarning.get("code").asText().toLowerCase());
            assertThat(warning.get("severity").asText()).isEqualTo(siteWarning.get("severity").asText().toLowerCase());
            assertThat(warning.get("field").asText()).isEqualTo(siteWarning.get("field").asText());
        }
        assertThat(result.getResponse().getContentAsString()).doesNotContain(FILE_NAME, "image/png", "message", "year",
                "sourceLineRange", "\"x\"", "\"width\"");
    }

    @Test
    void withNoLinesTheRawTextIsTheFallbackAndMissingFieldsAreNull() throws Exception {
        doReturn(new OcrResult("made-up unreadable text", 40, List.of())).when(extractor).extract(any());
        JsonNode api = body(preview(token, key(), png(), 200));
        assertThat(api.get("lines")).isEmpty();
        assertThat(api.get("rawText").asText()).isEqualTo("made-up unreadable text");
        assertThat(api.get("meanConfidence").asInt()).isEqualTo(40);
        JsonNode station = api.get("shifts").get(0).get("station");
        assertThat(station.get("value").isNull()).isTrue();
        assertThat(station.get("level").asText()).isEqualTo("missing");
        assertThat(station.get("confidence").isNull()).isTrue();
        assertThat(station.get("lineIndex").isNull()).isTrue();
        assertThat(codes(api.get("shifts").get(0).get("warnings"))).contains("missing_field");
    }

    @Test
    void duplicatesListOnlyThisAccountsBlocksAsCodes() throws Exception {
        Shift mine = scheduled(driver);
        scheduled(other);
        JsonNode duplicates = body(preview(token, key(), png(), 200)).get("shifts").get(0).get("duplicates");
        assertThat(duplicates).hasSize(1);
        assertThat(names(duplicates.get(0))).isEqualTo(Set.of("shiftId", "kind"));
        assertThat(duplicates.get(0).get("shiftId").asLong()).isEqualTo(mine.getId());
        assertThat(duplicates.get(0).get("kind").asText()).isEqualTo("scheduled_match");
    }

    @Test
    void uploadAndKeyProblemsAnswerCodesWithoutEchoingTheScreenshot() throws Exception {
        assertThat(body(mvc.perform(multipart("/api/v1/shifts/import-preview").file(png()).header("Authorization",
                "Bearer " + token)).andExpect(status().isBadRequest()).andReturn()).get("fields")
                .get("Idempotency-Key").asText()).isEqualTo("invalid");
        assertThat(body(preview(token, "not-a-uuid", png(), 400)).get("fields").get("Idempotency-Key").asText())
                .isEqualTo("invalid");
        assertThat(reason(preview(token, key(), new MockMultipartFile("screenshot", FILE_NAME, "image/gif",
                pngBytes()), 400))).isEqualTo("error.import.unsupportedType");
        assertThat(reason(preview(token, key(), new MockMultipartFile("screenshot", FILE_NAME, "image/png",
                "made-up not an image".getBytes()), 400))).isEqualTo("error.import.notReadableImage");
        assertThat(reason(preview(token, key(), new MockMultipartFile("other", FILE_NAME, "image/png", pngBytes()),
                400))).isEqualTo("error.import.noScreenshot");
        MvcResult notMultipart = mvc.perform(post("/api/v1/shifts/import-preview")
                .header("Authorization", "Bearer " + token).header("Idempotency-Key", key())
                .contentType("application/json").content("{}")).andExpect(status().isBadRequest()).andReturn();
        assertThat(body(notMultipart).get("error").asText()).isEqualTo("invalid");
        assertThat(notMultipart.getResponse().getHeader("Cache-Control")).contains("no-store");
        verify(extractor, never()).extract(any());
        assertThat(receipts.count()).isZero();
    }

    @Test
    void busyAndFailedReadsAnswerCodesAndLogNoScreenshotContent(CapturedOutput output) throws Exception {
        preview(token, key(), png(), 200);
        doThrow(new ScreenshotBusyException("error.import.busy")).when(extractor).extract(any());
        assertThat(body(preview(token, key(), png(), 503)).get("error").asText()).isEqualTo("busy");
        doThrow(new ScreenshotOcrException(
                new IllegalStateException("Made-up private note 4417"), "error.import.couldNotRead")).when(extractor).extract(any());
        MvcResult failed = preview(token, key(), png(), 500);
        assertThat(body(failed).get("error").asText()).isEqualTo("read_failed");
        assertThat(failed.getResponse().getHeader("Cache-Control")).contains("no-store");
        doReturn(OcrResult.fromLines(LINES)).when(extractor).extract(any());
        preview(token, key(), new MockMultipartFile("screenshot", FILE_NAME, "image/gif", pngBytes()), 400);

        assertThat(output.getAll()).contains("screenshot text extraction failed");
        for (OcrLine line : LINES) assertThat(output.getAll()).doesNotContain(line.text());
        assertThat(output.getAll()).doesNotContain(FILE_NAME, "DCY1", "72.00", "18.50", "90.50");
    }

    @Test
    void aKeyThatAlreadySavedABlockIsAnsweredWithoutReadingAgain() throws Exception {
        String screenshotKey = key();
        long id = body(write(post("/api/v1/shifts"), screenshotKey, block("completed"), 201)).get("id").asLong();
        clearInvocations(extractor);
        JsonNode answer = body(preview(token, screenshotKey.toUpperCase(), png(), 409));
        assertThat(answer.get("error").asText()).isEqualTo("already_saved");
        assertThat(answer.get("id").asLong()).isEqualTo(id);
        verify(extractor, never()).extract(any());

        String expenseKey = key();
        write(post("/api/v1/expenses"), expenseKey,
                "{\"date\":\"2026-11-12\",\"category\":\"fuel\",\"amount\":\"5.00\",\"note\":null,\"shiftId\":null}", 201);
        assertThat(body(preview(token, expenseKey, png(), 409)).get("error").asText()).isEqualTo("idempotency_conflict");
        // Another account's key is never this account's receipt.
        mvcPreview(otherToken, screenshotKey, png(), 200);
        verify(extractor, times(1)).extract(any());
    }

    @Test
    void aPreviewConsumesNoKeyAndOneScreenshotCanOnlyBeSavedOnce() throws Exception {
        Shift planned = scheduled(driver);
        String addKey = key();
        preview(token, addKey, png(), 200);
        assertThat(receipts.count()).isZero();
        // Add first, then complete with the same screenshot key: the second never lands.
        write(post("/api/v1/shifts"), addKey, block("completed"), 201);
        write(patch("/api/v1/shifts/" + planned.getId() + "/status"), addKey, complete(planned), 409);
        assertThat(statusOf(planned)).isEqualTo(ShiftStatus.SCHEDULED);
        assertThat(shifts.countByOwnerEmailIgnoreCase(EMAIL)).isEqualTo(2);
        write(post("/api/v1/shifts"), addKey, block("completed"), 201);
        assertThat(shifts.countByOwnerEmailIgnoreCase(EMAIL)).isEqualTo(2);

        // Complete first, then add with the same key: the add never lands.
        Shift second = scheduled(driver);
        String completeKey = key();
        String completeBody = complete(second);
        write(patch("/api/v1/shifts/" + second.getId() + "/status"), completeKey, completeBody, 200);
        write(post("/api/v1/shifts"), completeKey, block("completed"), 409);
        assertThat(shifts.countByOwnerEmailIgnoreCase(EMAIL)).isEqualTo(3);
        assertThat(statusOf(second)).isEqualTo(ShiftStatus.COMPLETED);
        // A delayed retry of the completion is acknowledged, not applied again.
        write(patch("/api/v1/shifts/" + second.getId() + "/status"), completeKey, completeBody, 200);
        assertThat(body(preview(token, completeKey, png(), 409)).get("id").asLong()).isEqualTo(second.getId());
    }

    @Test
    void oneAccountHasOnePreviewInFlightAndOtherAccountsAreNotRefused() throws Exception {
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(extractor.extract(any())).thenAnswer(invocation -> {
            inside.countDown();
            assertThat(release.await(10, TimeUnit.SECONDS)).isTrue();
            return OcrResult.fromLines(LINES);
        });
        CompletableFuture<MvcResult> first = CompletableFuture.supplyAsync(() -> mvcPreview(token, key(), png(), 200));
        assertThat(inside.await(10, TimeUnit.SECONDS)).isTrue();

        MvcResult refused = preview(token, key(), png(), 429);
        assertThat(body(refused).get("error").asText()).isEqualTo("busy_account");
        assertThat(refused.getResponse().getHeader("Retry-After")).isEqualTo("5");
        assertThat(refused.getResponse().getHeader("Cache-Control")).contains("no-store");
        // The other account waits for the shared reader instead of being refused.
        CompletableFuture<MvcResult> otherPreview = CompletableFuture.supplyAsync(
                () -> mvcPreview(otherToken, key(), png(), 200));
        Thread.sleep(300);
        assertThat(otherPreview).isNotDone();
        release.countDown();
        assertThat(first.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        assertThat(otherPreview.get(10, TimeUnit.SECONDS).getResponse().getStatus()).isEqualTo(200);
        verify(extractor, times(2)).extract(any());
        // Released after success: the same account can read again.
        preview(token, key(), png(), 200);
    }

    @Test
    void theAccountLockIsReleasedOnEveryOutcome() throws Exception {
        String saved = key();
        write(post("/api/v1/shifts"), saved, block("completed"), 201);
        List<Runnable> outcomes = new ArrayList<>(List.of(
                () -> mvcPreview(token, saved, png(), 409),
                () -> mvcPreview(token, key(), new MockMultipartFile("screenshot", FILE_NAME, "image/gif", pngBytes()), 400),
                () -> mvcPreview(token, key(), new MockMultipartFile("screenshot", FILE_NAME, "image/png",
                        "made-up not an image".getBytes()), 400)));
        for (Runnable outcome : outcomes) {
            outcome.run();
            preview(token, key(), png(), 200);
        }
        doThrow(new ScreenshotOcrException(new IllegalStateException("x"),
                "error.import.couldNotRead")).when(extractor).extract(any());
        preview(token, key(), png(), 500);
        doThrow(new ScreenshotBusyException("error.import.busy")).when(extractor).extract(any());
        preview(token, key(), png(), 503);
        doReturn(OcrResult.fromLines(LINES)).when(extractor).extract(any());
        preview(token, key(), png(), 200);
    }

    private MvcResult preview(String bearer, String key, MockMultipartFile file, int expected) throws Exception {
        MockMultipartHttpServletRequestBuilder request = multipart("/api/v1/shifts/import-preview").file(file);
        request.header("Authorization", "Bearer " + bearer).header("Idempotency-Key", key);
        return mvc.perform(request).andExpect(status().is(expected)).andReturn();
    }

    private MvcResult mvcPreview(String bearer, String key, MockMultipartFile file, int expected) {
        try {
            return preview(bearer, key, file, expected);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        } catch (AssertionError error) {
            throw new IllegalStateException(error);
        }
    }

    private MvcResult write(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
            String key, String payload, int expected) throws Exception {
        request.header("Authorization", "Bearer " + token).header("Idempotency-Key", key)
                .contentType("application/json").content(payload);
        return mvc.perform(request).andExpect(status().is(expected)).andReturn();
    }

    private static String block(String status) {
        return "{\"station\":\"DCY1\",\"date\":\"2026-11-12\",\"startTime\":\"04:00\",\"endTime\":\"07:30\","
                + "\"basePay\":\"72.00\",\"tips\":\"18.50\",\"status\":\"" + status + "\"}";
    }

    private String complete(Shift shift) {
        Instant updatedAt = shifts.findById(shift.getId()).orElseThrow().getUpdatedAt();
        return "{\"status\":\"completed\",\"basePay\":\"72.00\",\"tips\":\"18.50\",\"expectedUpdatedAt\":\""
                + updatedAt + "\"}";
    }

    private ShiftStatus statusOf(Shift shift) {
        return shifts.findById(shift.getId()).orElseThrow().getStatus();
    }

    private Shift scheduled(AppUser owner) {
        Shift shift = new Shift(null, "DCY1", LocalDate.of(2026, 11, 12), LocalTime.of(4, 0), LocalTime.of(7, 30),
                new BigDecimal("72.00"), BigDecimal.ZERO, owner);
        shift.setStatus(ShiftStatus.SCHEDULED);
        return shifts.saveAndFlush(shift);
    }

    private AppUser account(String name, String email) {
        AppUser user = new AppUser(name, email, encoder.encode(PASSWORD));
        user.setTimeZone("America/New_York");
        return users.save(user);
    }

    private String signIn(String email) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/sign-in").contentType("application/json")
                        .content(json.writeValueAsString(Map.of("email", email, "password", PASSWORD))))
                .andExpect(status().isOk()).andReturn();
        return body(result).get("token").asText();
    }

    private static MockMultipartFile png() {
        return new MockMultipartFile("screenshot", FILE_NAME, "image/png", pngBytes());
    }

    private static byte[] pngBytes() {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(new BufferedImage(40, 80, BufferedImage.TYPE_INT_RGB), "png", out);
            return out.toByteArray();
        } catch (java.io.IOException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static OcrLine line(String text, int confidence, int index) {
        return new OcrLine(text, confidence, 0, index * 20, 200, 18, index);
    }

    private static String key() { return UUID.randomUUID().toString(); }

    private JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String reason(MvcResult result) throws Exception {
        JsonNode body = body(result);
        assertThat(body.get("error").asText()).isEqualTo("invalid");
        return body.get("reason").asText();
    }

    private static Set<String> names(JsonNode node) {
        Set<String> names = new java.util.TreeSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    private static List<String> codes(JsonNode warnings) {
        List<String> codes = new ArrayList<>();
        for (JsonNode warning : warnings) codes.add(warning.get("code").asText());
        return codes;
    }
}
