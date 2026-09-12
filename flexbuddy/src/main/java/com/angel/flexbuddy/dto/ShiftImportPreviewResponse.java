package com.angel.flexbuddy.dto;

import java.util.List;

import com.angel.flexbuddy.service.OcrLine;

public record ShiftImportPreviewResponse(
        String originalFilename,
        String contentType,
        long size,
        String message,
        String rawText,
        int year,
        int meanConfidence,
        List<OcrLine> lines,
        List<ShiftCandidate> shifts
) {
    public ShiftImportPreviewResponse {
        lines = lines == null ? List.of() : List.copyOf(lines);
        shifts = shifts == null ? List.of() : List.copyOf(shifts);
    }
}
