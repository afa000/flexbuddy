package com.angel.flexbuddy.i18n;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Keeps the message file and the code that uses it in step, without starting Spring: a key used but missing shows as
 * {@code ??key??} on a page, a key never used is dead text a translator would still have to translate, and a doubled
 * (or missing) apostrophe changes what a driver reads.
 */
class MessageKeysTest {

    private static final String FILE = "i18n/messages.properties";
    private static final Path JAVA = Path.of("src", "main", "java");

    /** {@code #{key}} and {@code #{key(args)}} in a template. */
    private static final Pattern TEMPLATE_KEY = Pattern.compile("#\\{([A-Za-z0-9_.]+)");
    private static final Pattern UTEXT_KEY = Pattern.compile("th:utext=\"#\\{([A-Za-z0-9_.]+)");
    private static final Pattern PLAIN_KEY = Pattern.compile("th:(?!utext)[a-z-]+=\"#\\{([A-Za-z0-9_.]+)");
    /** {@code #messages.msg('key', ...)} inside an expression, such as a mailto link. */
    private static final Pattern TEMPLATE_MSG = Pattern.compile("#messages\\.msg\\('([A-Za-z0-9_.]+)'");
    /** {@code message = "{key}"} and {@code default "{key}"} on a validation annotation. */
    private static final Pattern VALIDATION_KEY = Pattern.compile("\"\\{([A-Za-z0-9_.]+)}\"");
    /** A string literal in the Java code that is shaped like a key of one of the groups in the file. */
    private static final Pattern JAVA_KEY = Pattern.compile(
            "\"((?:error|validation|import|restore|email|push|calendar)\\.[A-Za-z0-9_.]+)\"");
    /** A key passed to a lookup, whatever group it belongs to. */
    private static final Pattern JAVA_LOOKUP = Pattern.compile(
            "(?:Messages\\.(?:english|current|in)\\((?:[A-Za-z.]+, )?|getMessage\\()\"([A-Za-z0-9_.]+)\"");
    private static final Pattern ARGUMENT = Pattern.compile("\\{\\d+}");

    /** Keys only a browser script will use once the scripts read this file. */
    private static final Set<String> USED_BY_SCRIPTS = Set.of();

    private static String text(Resource resource) throws IOException {
        return resource.getContentAsString(StandardCharsets.UTF_8);
    }

    private static Properties messages() throws IOException {
        Properties properties = new Properties();
        properties.load(new StringReader(text(new ClassPathResource(FILE))));
        return properties;
    }

    private static Map<String, String> templates() throws IOException {
        Map<String, String> templates = new TreeMap<>();
        for (Resource template : new PathMatchingResourcePatternResolver().getResources("classpath:/templates/*.html")) {
            templates.put(template.getFilename(), text(template));
        }
        return templates;
    }

    private static Map<String, String> javaSources() throws IOException {
        Map<String, String> sources = new TreeMap<>();
        try (Stream<Path> files = Files.walk(JAVA)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                sources.put(JAVA.relativize(file).toString().replace('\\', '/'),
                        Files.readString(file, StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    private static void collect(Pattern pattern, String source, String where, Map<String, Set<String>> into) {
        Matcher matcher = pattern.matcher(source);
        while (matcher.find()) into.computeIfAbsent(matcher.group(1), key -> new TreeSet<>()).add(where);
    }

    private static Map<String, Set<String>> keysUsed() throws IOException {
        Map<String, Set<String>> used = new TreeMap<>();
        for (Map.Entry<String, String> template : templates().entrySet()) {
            collect(TEMPLATE_KEY, template.getValue(), template.getKey(), used);
            collect(TEMPLATE_MSG, template.getValue(), template.getKey(), used);
        }
        for (Map.Entry<String, String> source : javaSources().entrySet()) {
            collect(VALIDATION_KEY, source.getValue(), source.getKey(), used);
            collect(JAVA_KEY, source.getValue(), source.getKey(), used);
            collect(JAVA_LOOKUP, source.getValue(), source.getKey(), used);
        }
        return used;
    }

    @Test
    void everyKeyUsedIsInTheFile() throws Exception {
        Set<String> defined = messages().stringPropertyNames();
        List<String> missing = new ArrayList<>();
        keysUsed().forEach((key, where) -> {
            if (!defined.contains(key)) missing.add(key + " (in " + String.join(", ", where) + ")");
        });
        assertThat(missing).as("keys used that " + FILE + " does not have").isEmpty();
    }

    @Test
    void everyKeyInTheFileIsUsed() throws Exception {
        Set<String> used = new HashSet<>(keysUsed().keySet());
        used.addAll(USED_BY_SCRIPTS);
        List<String> unused = messages().stringPropertyNames().stream().filter(key -> !used.contains(key)).sorted().toList();
        assertThat(unused).as("keys in " + FILE + " that nothing uses").isEmpty();
    }

    @Test
    void aKeyForMarkupIsOnlyUsedWithUtextAndTheRestOnlyWithText() throws Exception {
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<String, String> template : templates().entrySet()) {
            Matcher utext = UTEXT_KEY.matcher(template.getValue());
            while (utext.find()) {
                if (!utext.group(1).endsWith(".html")) wrong.add(utext.group(1) + " is used with th:utext in " + template.getKey());
            }
            Matcher plain = PLAIN_KEY.matcher(template.getValue());
            while (plain.find()) {
                if (plain.group(1).endsWith(".html")) wrong.add(plain.group(1) + " holds markup but is used without th:utext in " + template.getKey());
            }
        }
        assertThat(wrong).isEmpty();
    }

    @Test
    void anApostropheIsDoubledOnlyInValuesThatTakeArguments() throws Exception {
        List<String> wrong = new ArrayList<>();
        Properties messages = messages();
        for (String key : new TreeSet<>(messages.stringPropertyNames())) {
            String value = messages.getProperty(key);
            if (ARGUMENT.matcher(value).find()) {
                // MessageFormat reads a lone ' as the start of quoted text and drops it, so it must be written ''.
                if (value.replace("''", "").contains("'")) wrong.add(key + " takes arguments, so a ' must be written ''");
            } else if (value.contains("''")) {
                wrong.add(key + " takes no arguments, so '' would be shown as two apostrophes");
            }
        }
        assertThat(wrong).isEmpty();
    }

    @Test
    void noKeyIsDefinedTwice() throws Exception {
        Set<String> seen = new HashSet<>();
        List<String> twice = new ArrayList<>();
        for (String line : text(new ClassPathResource(FILE)).split("\\R")) {
            if (line.isBlank() || line.startsWith("#") || !line.contains("=")) continue;
            String key = line.substring(0, line.indexOf('=')).trim();
            if (!seen.add(key)) twice.add(key);
        }
        assertThat(twice).isEmpty();
    }

    @Test
    void everyValueHasText() throws Exception {
        Properties messages = messages();
        List<String> empty = messages.stringPropertyNames().stream()
                .filter(key -> messages.getProperty(key).isBlank()).sorted().toList();
        assertThat(empty).isEmpty();
    }
}
