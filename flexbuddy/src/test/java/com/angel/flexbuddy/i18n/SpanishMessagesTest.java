package com.angel.flexbuddy.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

class SpanishMessagesTest {
    private static final Pattern ARG = Pattern.compile("\\{\\d+}");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    // Brands, standing names, shared Spanish words, and text-free formatting.
    private static final Set<String> UNCHANGED = Set.of(
            "app.name", "format.time", "errorPage.error404", "errorPage.error",
            "schedule.fantastic", "schedule.great", "schedule.fair", "schedule.atRisk",
            "accountSection.google", "taxSummary.flexbuddy",
            "js.calendar.dayDescription", "js.standing.fantastic", "js.standing.great",
            "js.standing.fair", "js.standing.atRisk", "js.charts.base", "js.charts.total",
            "js.schedule.dayShiftCount", "js.schedule.durationDaysHours", "js.app.sizeKb", "js.app.sizeMb");

    private String text(String suffix) throws Exception {
        return new ClassPathResource("i18n/messages" + suffix + ".properties").getContentAsString(StandardCharsets.UTF_8);
    }

    private Properties messages(String suffix) throws Exception {
        Properties result = new Properties();
        result.load(new StringReader(text(suffix)));
        return result;
    }

    @Test
    void catalogHasTheSameKeysOrderAndGroupCommentsWithoutDuplicates() throws Exception {
        List<String> en = structure(text(""));
        List<String> es = structure(text("_es"));
        assertThat(es).containsExactlyElementsOf(en);
        List<String> keys = es.stream().filter(s -> !s.isBlank() && !s.startsWith("#")).toList();
        assertThat(keys).doesNotHaveDuplicates();
        assertThat(messages("_es").stringPropertyNames()).isEqualTo(messages("").stringPropertyNames());
    }

    private List<String> structure(String text) {
        return text.lines().map(line -> line.startsWith("#") || line.isBlank()
                ? line : line.substring(0, line.indexOf('='))).toList();
    }

    @Test
    void translationsPreserveArgumentsMarkupAndApostropheRules() throws Exception {
        Properties en = messages(""), es = messages("_es");
        List<String> failures = new ArrayList<>();
        for (String key : en.stringPropertyNames()) {
            String value = es.getProperty(key);
            assertThat(value).as(key).isNotBlank();
            assertThat(ARG.matcher(value).results().map(m -> m.group()).sorted().toList())
                    .as(key).isEqualTo(ARG.matcher(en.getProperty(key)).results().map(m -> m.group()).sorted().toList());
            assertThat(TAG.matcher(value).results().map(m -> m.group()).toList())
                    .as(key).isEqualTo(TAG.matcher(en.getProperty(key)).results().map(m -> m.group()).toList());
            if (ARG.matcher(value).find() ? value.replace("''", "").contains("'") : value.contains("''")) {
                failures.add(key);
            }
            if (!UNCHANGED.contains(key)) assertThat(value).as(key).isNotEqualTo(en.getProperty(key));
        }
        assertThat(failures).isEmpty();
    }

    @Test
    void serverFormatsSpanishDatesAndSubstitutesArguments() {
        assertThat(Messages.dayFormat(UserLocales.SPANISH).format(java.time.LocalDate.of(2026, 9, 28)))
                .contains("28 de sept");
        assertThat(Messages.in(UserLocales.SPANISH, "push.upcoming.title", "VEA7"))
                .isEqualTo("Próximo bloque · VEA7");
    }
}
