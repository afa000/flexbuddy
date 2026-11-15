package com.angel.flexbuddy.service;

public record ParsedField<T>(T value, Integer confidence, Integer lineIndex, ConfidenceLevel level) {
    public ParsedField {
        var shared = new com.angel.flexbuddy.importcore.ParsedField<>(value, confidence, lineIndex,
                level == null ? null : com.angel.flexbuddy.importcore.ConfidenceLevel.valueOf(level.name()));
        confidence = shared.confidence();
        lineIndex = shared.lineIndex();
        level = ConfidenceLevel.valueOf(shared.level().name());
    }

    public static <T> ParsedField<T> found(T value, OcrLine line) {
        return value == null
                ? missing()
                : new ParsedField<>(value, line.confidence(), line.index(), null);
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

}
