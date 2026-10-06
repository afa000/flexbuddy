package com.angel.flexbuddy;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/** Checks that pages and the service worker agree about which static files exist, without starting Spring. */
class StaticAssetsTest {

    private static final Pattern SCRIPT = Pattern.compile("@\\{(/js/[\\w.-]+\\.js)");
    private static final Pattern VERSIONED_ASSETS = Pattern.compile("VERSIONED_ASSETS\\s*=\\s*\\[(.*?)]", Pattern.DOTALL);
    private static final Pattern ENTRY = Pattern.compile("'([^']+)'");

    @Test
    void everyPageScriptIsPrecachedByTheServiceWorker() throws Exception {
        String worker = new ClassPathResource("static/sw.js").getContentAsString(StandardCharsets.UTF_8);
        Matcher array = VERSIONED_ASSETS.matcher(worker);
        assertThat(array.find()).as("VERSIONED_ASSETS in sw.js").isTrue();
        List<String> precached = new ArrayList<>();
        Matcher entries = ENTRY.matcher(array.group(1));
        while (entries.find()) precached.add(entries.group(1));

        List<String> missing = new ArrayList<>();
        for (Resource template : new PathMatchingResourcePatternResolver().getResources("classpath:/templates/*.html")) {
            Matcher scripts = SCRIPT.matcher(template.getContentAsString(StandardCharsets.UTF_8));
            while (scripts.find()) {
                if (!precached.contains(scripts.group(1))) missing.add(scripts.group(1) + " in " + template.getFilename());
            }
        }
        assertThat(missing).as("scripts a page loads that sw.js VERSIONED_ASSETS does not list").isEmpty();
    }

    @Test
    void dateAndTimeInputsAreNormalisedForIos() throws Exception {
        String css = new ClassPathResource("static/css/styles.css").getContentAsString(StandardCharsets.UTF_8);
        String why = "iOS draws date and time inputs wider than their column without these rules";

        assertThat(css).as(why).contains("input[type=\"date\"]::-webkit-date-and-time-value");
        Matcher block = Pattern.compile("\\.field input\\[type=\"date\"\\][^{]*\\{[^}]*}").matcher(css);
        assertThat(block.find()).as(why).isTrue();
        assertThat(block.group()).as(why).contains("-webkit-appearance: none");
        assertThat(css).as(why).containsPattern("\\.preview-form \\{[^}]*minmax\\(0, 1fr\\)");
    }
}
