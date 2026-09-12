package com.angel.flexbuddy.service;

public record OcrLine(
        String text,
        int confidence,
        int x,
        int y,
        int width,
        int height,
        int index
) {
    public OcrLine {
        text = text == null ? "" : text.trim();
        confidence = Math.max(0, Math.min(100, confidence));
    }
}
