package com.angel.flexbuddy.importcore;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The native Android app (min SDK 26, Android 8) runs this library. Stream.toList() and Matcher.results() only
 * exist on Android from API 34 (Android 14), and D8 does not backport them, so on Android 8 to 13 any call throws
 * NoSuchMethodError and the screenshot import fails. Use Collectors.toList() or a list copy, and a
 * {@code while (matcher.find())} loop with {@code matcher.toMatchResult()}, instead.
 *
 * <p>Java 21's sequenced-collection methods ({@code getFirst}, {@code getLast}, {@code removeFirst},
 * {@code removeLast}, {@code addFirst}, {@code addLast} and {@code reversed}) only exist on Android's {@code List}
 * from API 35, so they are refused too: use {@code get(0)} and {@code get(size() - 1)}.
 *
 * <p>This is a source scan of those methods only; the native app's scan of the release JAR against Android's API
 * database is the full check.
 */
class AndroidApiCompatibilityTest {
    private static final Path MAIN_SOURCES = Path.of("src", "main", "java");
    /** Collectors.toList() and Collectors::toList exist from API 24 and are allowed. */
    private static final String NOT_COLLECTORS = "(?<!\\bCollectors\\s{0,40})";
    /** Each pattern runs over a whole file without comments, so a call split across lines is still found. */
    private static final Map<String, Pattern> FORBIDDEN = Map.of(
            "Stream.toList()", Pattern.compile(NOT_COLLECTORS + "\\.\\s*toList\\s*\\(\\s*\\)"),
            "Matcher.results()", Pattern.compile("\\.\\s*results\\s*\\(\\s*\\)"),
            "Stream::toList / Matcher::results", Pattern.compile(NOT_COLLECTORS + "::\\s*(?:toList|results)\\b"),
            "a sequenced-collection method", Pattern.compile(
                    "\\.\\s*(?:getFirst|getLast|removeFirst|removeLast|reversed)\\s*\\(\\s*\\)"
                            + "|\\.\\s*(?:addFirst|addLast)\\s*\\("),
            "a sequenced-collection method reference",
            Pattern.compile("::\\s*(?:getFirst|getLast|removeFirst|removeLast|addFirst|addLast|reversed)\\b"));

    @Test
    void mainSourcesAvoidMethodsAndroidOnlyHasFromApi34() throws IOException {
        assertTrue(Files.isDirectory(MAIN_SOURCES), "Run from the import-core module: " + MAIN_SOURCES.toAbsolutePath());
        List<String> found = new ArrayList<>();
        List<Path> sources;
        try (Stream<Path> paths = Files.walk(MAIN_SOURCES)) {
            sources = paths.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        for (Path source : sources) {
            for (String hit : forbiddenCalls(Files.readString(source, StandardCharsets.UTF_8))) {
                found.add(source + ":" + hit);
            }
        }
        assertTrue(sources.size() > 0, "No main sources found under " + MAIN_SOURCES.toAbsolutePath());
        assertEquals(List.of(), found, "Android below API 34 (Android 14) has no Stream.toList() or "
                + "Matcher.results(), below API 35 no List.getFirst() and the other sequenced-collection methods, "
                + "and D8 does not backport them: replace these calls");
    }

    @Test
    void theScanCatchesCallsSplitLinesAndMethodReferences() {
        assertEquals(1, forbiddenCalls("values.stream().distinct().toList();").size());
        assertEquals(1, forbiddenCalls("    .filter(line -> !line.isBlank())\n            .toList();").size());
        assertEquals(1, forbiddenCalls("values.stream().distinct().\n    toList();").size());
        assertEquals(1, forbiddenCalls("PATTERN.matcher(text)\n    .results();").size());
        assertEquals(2, forbiddenCalls("PATTERN.matcher(text).results().toList()").size());
        assertEquals(1, forbiddenCalls("Function<Stream<String>, List<String>> f = Stream::toList;").size());
        assertEquals(1, forbiddenCalls("Function<Matcher, Stream<MatchResult>> f = Matcher :: results;").size());
    }

    @Test
    void theScanCatchesSequencedCollectionMethods() {
        assertEquals(1, forbiddenCalls("sample.blocks().getFirst().station()").size());
        assertEquals(1, forbiddenCalls("blocks.getLast()").size());
        assertEquals(1, forbiddenCalls("blocks\n    .reversed()").size());
        assertEquals(1, forbiddenCalls("queue.removeFirst()").size());
        assertEquals(1, forbiddenCalls("list.addFirst(block)").size());
        assertEquals(1, forbiddenCalls("Function<List<Block>, Block> f = List::getFirst;").size());
        assertEquals(List.of(), forbiddenCalls("blocks.get(0); blocks.get(blocks.size() - 1); firstBlock()"));
    }

    @Test
    void theScanAllowsCollectorsAndComments() {
        assertEquals(List.of(), forbiddenCalls(".collect(Collectors.toList())"));
        assertEquals(List.of(), forbiddenCalls(".collect(Collectors\n        .toList())"));
        assertEquals(List.of(), forbiddenCalls("Supplier<Collector<String, ?, List<String>>> s = Collectors::toList;"));
        assertEquals(List.of(), forbiddenCalls("matchResults(PATTERN.matcher(text))"));
        assertEquals(List.of(), forbiddenCalls("code(); // .toList() in a comment\n/**\n * matcher.results()\n */"));
    }

    /** Returns "line: call" for each forbidden call in one source file. */
    private static List<String> forbiddenCalls(String source) {
        String code = withoutComments(source);
        List<String> hits = new ArrayList<>();
        FORBIDDEN.forEach((name, pattern) -> {
            Matcher matcher = pattern.matcher(code);
            while (matcher.find()) {
                int line = 1 + (int) code.substring(0, matcher.start()).chars().filter(c -> c == '\n').count();
                hits.add(line + " " + name);
            }
        });
        hits.sort(null);
        return hits;
    }

    /** Comments may name the forbidden calls to explain them; only code counts. Line numbers are kept. */
    private static String withoutComments(String source) {
        StringBuilder code = new StringBuilder();
        for (String line : source.split("\\R", -1)) {
            String trimmed = line.stripLeading();
            if (trimmed.startsWith("*") || trimmed.startsWith("/*")) {
                line = "";
            } else {
                int comment = line.indexOf("//");
                if (comment >= 0) line = line.substring(0, comment);
            }
            code.append(line).append('\n');
        }
        return code.toString();
    }
}
