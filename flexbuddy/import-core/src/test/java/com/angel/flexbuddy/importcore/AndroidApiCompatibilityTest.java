package com.angel.flexbuddy.importcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The native Android app (min SDK 26, Android 8) runs this library. Stream.toList() and Matcher.results() only
 * exist on Android from API 34 (Android 14), and D8 does not backport them, so on Android 8 to 13 any call throws
 * NoSuchMethodError and the screenshot import fails. Use Collectors.toList() or a list copy, and a
 * {@code while (matcher.find())} loop with {@code matcher.toMatchResult()}, instead.
 */
class AndroidApiCompatibilityTest {
    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");
    /** Stream.toList(); Collectors.toList() is available from API 24 and is allowed. */
    private static final Pattern STREAM_TO_LIST = Pattern.compile("(?<!\\bCollectors)\\s*\\.\\s*toList\\s*\\(\\s*\\)");
    private static final Pattern MATCHER_RESULTS = Pattern.compile("\\.\\s*results\\s*\\(\\s*\\)");

    @Test
    void mainSourcesAvoidMethodsAndroidOnlyHasFromApi34() throws IOException {
        assertTrue(Files.isDirectory(MAIN_SOURCES), "Run from the import-core module: " + MAIN_SOURCES.toAbsolutePath());
        List<String> found = new ArrayList<>();
        int files;
        try (Stream<Path> paths = Files.walk(MAIN_SOURCES)) {
            List<Path> sources = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
            files = sources.size();
            for (Path source : sources) {
                List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
                for (int index = 0; index < lines.size(); index++) {
                    String code = withoutLineComment(lines.get(index));
                    if (STREAM_TO_LIST.matcher(code).find()) {
                        found.add(source + ":" + (index + 1) + " Stream.toList()");
                    }
                    if (MATCHER_RESULTS.matcher(code).find()) {
                        found.add(source + ":" + (index + 1) + " Matcher.results()");
                    }
                }
            }
        }
        assertTrue(files > 0, "No main sources found under " + MAIN_SOURCES.toAbsolutePath());
        assertEquals(List.of(), found, "Android below API 34 (Android 14) has no Stream.toList() or "
                + "Matcher.results(), and D8 does not backport them: replace these calls");
    }

    @Test
    void theScanCatchesBothCallsAndAllowsCollectorsToList() {
        assertTrue(STREAM_TO_LIST.matcher("values.stream().distinct().toList();").find());
        assertTrue(STREAM_TO_LIST.matcher("    .filter(line -> !line.isBlank())\n.toList()").find());
        assertTrue(MATCHER_RESULTS.matcher("PATTERN.matcher(text).results().toList()").find());
        assertFalse(STREAM_TO_LIST.matcher(".collect(Collectors.toList())").find());
        assertFalse(MATCHER_RESULTS.matcher("matchResults(PATTERN.matcher(text))").find());
        assertEquals("code ", withoutLineComment("code // .toList() in a comment"));
    }

    /** Comments may name the forbidden calls to explain them; only code counts. */
    private static String withoutLineComment(String line) {
        String trimmed = line.stripLeading();
        if (trimmed.startsWith("*") || trimmed.startsWith("/*")) return "";
        Matcher comment = Pattern.compile("//").matcher(line);
        return comment.find() ? line.substring(0, comment.start()) : line;
    }
}
