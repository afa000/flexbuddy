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
        String text = safeLines.stream()
                .map(OcrLine::text)
                .filter(line -> !line.isBlank())
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse("");

        int totalWeight = safeLines.stream().mapToInt(line -> Math.max(1, line.text().length())).sum();
        int mean = totalWeight == 0 ? 0 : (int) Math.round(
                safeLines.stream()
                        .mapToDouble(line -> line.confidence() * Math.max(1, line.text().length()))
                        .sum() / totalWeight
        );
        return new OcrResult(text, mean, safeLines);
    }
}
