package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.i18n.LocaleContextHolder;

import com.angel.flexbuddy.i18n.Messages;

class ImportCoreLocalizationTest {
    @ParameterizedTest
    @ValueSource(strings = {"en", "es"})
    void translatesParserAndRuleWarningsOnceWhilePreservingExistingMessages(String language) {
        Locale previous = LocaleContextHolder.getLocale();
        Locale locale = Locale.forLanguageTag(language);
        LocaleContextHolder.setLocale(locale);
        try {
            var context = new ParseContext(LocalDate.of(2027, 1, 5), null);
            var parsed = new ShiftScreenshotParser().parse(
                    List.of(new OcrLine("Monday, 12/28", 95, 0, 0, 0, 0, 0)), 2027, context);
            assertThat(parsed.warnings()).containsExactly(new ImportWarning("YEAR_ROLLOVER", WarningSeverity.INFO,
                    "date", Messages.in(locale, "import.warning.yearRollover")));
            var rules = new ImportWarningRules();
            var result = rules.apply(parsed, 95, context);
            assertThat(result.warnings()).extracting(ImportWarning::message).containsExactly(
                    Messages.in(locale, "import.warning.yearRollover"),
                    Messages.in(locale, "import.warning.stationMissing"),
                    Messages.in(locale, "import.warning.startTimeMissing"),
                    Messages.in(locale, "import.warning.endTimeMissing"),
                    Messages.in(locale, "import.warning.basePayMissing"));
            assertThat(rules.apply(result, 95, context)).isEqualTo(result);
            assertThat(result.warnings()).allMatch(w -> !w.message().startsWith("import.warning."));
        } finally {
            LocaleContextHolder.setLocale(previous);
        }
    }
}
