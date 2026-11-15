package com.angel.flexbuddy.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Component;

/** Keeps the website's parser API and translated warnings while sharing the parsing rules with Android. */
@Component
public class ShiftScreenshotParser {
    private final com.angel.flexbuddy.importcore.ShiftScreenshotParser parser =
            new com.angel.flexbuddy.importcore.ShiftScreenshotParser();

    public ParsedShiftData parse(String rawText, int year) {
        return ImportCoreAdapter.fromCore(parser.parse(rawText, year,
                new com.angel.flexbuddy.importcore.ParseContext(LocalDate.now(), null)));
    }

    public ParsedShiftData parse(List<OcrLine> lines, int year, ParseContext context) {
        return ImportCoreAdapter.fromCore(parser.parse(toCoreLines(lines), year, ImportCoreAdapter.toCore(context)));
    }

    static boolean looksSpanish(List<OcrLine> lines) {
        return com.angel.flexbuddy.importcore.ShiftScreenshotParser.looksSpanish(toCoreLines(lines));
    }

    private static List<com.angel.flexbuddy.importcore.OcrLine> toCoreLines(List<OcrLine> lines) {
        return lines == null ? List.of() : lines.stream().map(ImportCoreAdapter::toCore).toList();
    }
}
