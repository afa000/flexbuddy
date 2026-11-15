package com.angel.flexbuddy.api;

import java.util.List;
import java.util.function.Function;

import com.angel.flexbuddy.dto.DuplicateMatch;
import com.angel.flexbuddy.dto.ShiftCandidate;
import com.angel.flexbuddy.dto.ShiftImportPreviewResponse;
import com.angel.flexbuddy.service.ImportWarning;
import com.angel.flexbuddy.service.OcrLine;
import com.angel.flexbuddy.service.ParsedField;

/**
 * NA-07: what the website's import review shows a driver, in the API conventions. Every value is copied from
 * {@link ShiftImportPreviewResponse}; nothing is recomputed here. Left out because the review doesn't show them: line
 * boxes, per-line confidence, the source line range, the year, file details and translated sentences.
 */
record ApiImportPreview(int meanConfidence, List<Line> lines, String rawText, List<Candidate> shifts) {
    record Line(int index, String text) { }

    record Field(String value, String level, Integer confidence, Integer lineIndex) { }

    record Warning(String code, String severity, String field) { }

    record Duplicate(Long shiftId, String kind) { }

    record Candidate(Field station, Field date, Field startTime, Field endTime, Field basePay, Field tips,
            String suggestedStatus, List<Warning> warnings, List<Duplicate> duplicates) { }

    static ApiImportPreview of(ShiftImportPreviewResponse preview) {
        List<Line> lines = preview.lines().stream().map(ApiImportPreview::line).toList();
        // The website shows the raw text only when there are no lines to show.
        String rawText = lines.isEmpty() && !preview.rawText().isEmpty() ? preview.rawText() : null;
        return new ApiImportPreview(preview.meanConfidence(), lines, rawText,
                preview.shifts().stream().map(ApiImportPreview::candidate).toList());
    }

    private static Line line(OcrLine line) {
        return new Line(line.index(), line.text());
    }

    private static Candidate candidate(ShiftCandidate shift) {
        return new Candidate(
                field(shift.station(), value -> value),
                field(shift.date(), ApiValues::date),
                field(shift.startTime(), ApiValues::time),
                field(shift.endTime(), ApiValues::time),
                field(shift.basePay(), ApiValues::money),
                field(shift.tips(), ApiValues::money),
                ApiValues.code(shift.suggestedStatus()),
                shift.warnings().stream().map(ApiImportPreview::warning).toList(),
                shift.duplicates().stream().map(ApiImportPreview::duplicate).toList());
    }

    private static <T> Field field(ParsedField<T> field, Function<T, String> format) {
        if (field == null) return new Field(null, "missing", null, null);
        return new Field(field.value() == null ? null : format.apply(field.value()), ApiValues.code(field.level()),
                field.confidence(), field.lineIndex());
    }

    private static Warning warning(ImportWarning warning) {
        return new Warning(warning.code() == null ? null : warning.code().toLowerCase(java.util.Locale.ROOT),
                ApiValues.code(warning.severity()), warning.field());
    }

    private static Duplicate duplicate(DuplicateMatch match) {
        return new Duplicate(match.shiftId(), ApiValues.code(match.kind()));
    }
}
