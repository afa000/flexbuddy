package com.angel.flexbuddy.alert;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

class ErrorAlertTextTest {

    private static final Instant AT = Instant.parse("2026-10-02T14:03:30Z");

    private final ErrorReport server = new ErrorReport(ErrorReport.Source.SERVER, "DataIntegrityViolationException",
            "ShiftService.update(ShiftService.java:212)", List.of("caused by PSQLException", "ShiftService.update(ShiftService.java:212)"),
            null, null, "20261002.1");
    private final ErrorReport browser = new ErrorReport(ErrorReport.Source.BROWSER, "TypeError", "/js/home.js:12:5",
            List.of("TypeError: x is null"), 42L, "home", "20261002.1");

    @Test
    void subjectsNameTheKindAndThePlace() {
        assertThat(ErrorAlertText.subject(server))
                .isEqualTo("[FlexBuddy] Server error: DataIntegrityViolationException at ShiftService.update");
        assertThat(ErrorAlertText.subject(browser)).isEqualTo("[FlexBuddy] Browser error: TypeError in /js/home.js");
    }

    @Test
    void aSubjectIsCutTo150Characters() {
        ErrorReport long_ = new ErrorReport(ErrorReport.Source.SERVER, "K".repeat(200), "Where", List.of(), null, null, "b");

        assertThat(ErrorAlertText.subject(long_)).hasSize(150);
    }

    @Test
    void theBodyHasTheTimeInUtcTheVersionAndThePlace() {
        String body = ErrorAlertText.body(server, AT, 0);

        assertThat(body).contains("When: 2026-10-02 14:03 UTC")
                .contains("Version: 20261002.1")
                .contains("Where: ShiftService.update(ShiftService.java:212)")
                .contains("caused by PSQLException")
                .contains("Search the Render logs around this time for the full trace.");
    }

    @Test
    void onlyBrowserReportsNameTheScreenAndAccount() {
        assertThat(ErrorAlertText.body(browser, AT, 0)).contains("Screen: home").contains("Account: #42");
        assertThat(ErrorAlertText.body(server, AT, 0)).doesNotContain("Account:").doesNotContain("Screen:");
    }

    @Test
    void theRepeatLineAppearsOnlyWhenThereWereRepeats() {
        assertThat(ErrorAlertText.body(server, AT, 0)).doesNotContain("Repeated");
        assertThat(ErrorAlertText.body(server, AT, 3)).contains("Repeated 3 more times since the last email");
    }
}
