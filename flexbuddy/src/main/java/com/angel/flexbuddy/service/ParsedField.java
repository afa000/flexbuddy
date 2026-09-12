package com.angel.flexbuddy.service;

public record ParsedField<T>(T value, Integer confidence, Integer lineIndex, ConfidenceLevel level) {
    public ParsedField {
        if (value == null) {
            confidence = null;
            lineIndex = null;
            level = ConfidenceLevel.MISSING;
        } else if (level == null) {
            level = levelFor(confidence);
        }
    }

    public static <T> ParsedField<T> found(T value, OcrLine line) {
        return value == null
                ? missing()
                : new ParsedField<>(value, line.confidence(), line.index(), levelFor(line.confidence()));
    }

    public static <T> ParsedField<T> defaulted(T value) {
        return new ParsedField<>(value, 100, null, ConfidenceLevel.HIGH);
    }

    public static <T> ParsedField<T> missing() {
        return new ParsedField<>(null, null, null, ConfidenceLevel.MISSING);
    }

    public ParsedField<T> withValue(T replacement) {
        return new ParsedField<>(replacement, confidence, lineIndex, level);
    }

    private static ConfidenceLevel levelFor(Integer confidence) {
        if (confidence == null || confidence >= 80) return ConfidenceLevel.HIGH;
        if (confidence >= 60) return ConfidenceLevel.MEDIUM;
        return ConfidenceLevel.LOW;
    }
}
