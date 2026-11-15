package com.angel.flexbuddy.service;

import java.util.List;

public record OcrResult(String text, int meanConfidence, List<OcrLine> lines) {
    public OcrResult {
        text = text == null ? "" : text.trim();
        meanConfidence = Math.max(0, Math.min(100, meanConfidence));
        lines = lines == null ? List.of() : List.copyOf(lines);
    }

    public static OcrResult fromLines(List<OcrLine> lines) {
        List<OcrLine> safeLines = lines == null ? List.of() : List.copyOf(lines);
        var shared = com.angel.flexbuddy.importcore.OcrResult.fromLines(
                safeLines.stream().map(ImportCoreAdapter::toCore).toList());
        return new OcrResult(shared.text(), shared.meanConfidence(), safeLines);
    }
}
