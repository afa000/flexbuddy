package com.angel.flexbuddy.service;

import java.util.List;

import com.angel.flexbuddy.i18n.Messages;

/** Maps the shared value types to the website's existing response types and request-language messages. */
final class ImportCoreAdapter {
    private ImportCoreAdapter() {}

    static com.angel.flexbuddy.importcore.OcrLine toCore(OcrLine line) {
        return new com.angel.flexbuddy.importcore.OcrLine(line.text(), line.confidence(), line.x(), line.y(),
                line.width(), line.height(), line.index());
    }

    static com.angel.flexbuddy.importcore.ParseContext toCore(ParseContext context) {
        return new com.angel.flexbuddy.importcore.ParseContext(context.today(), context.inheritedDate());
    }

    static <T> com.angel.flexbuddy.importcore.ParsedField<T> toCore(ParsedField<T> field) {
        return new com.angel.flexbuddy.importcore.ParsedField<>(field.value(), field.confidence(), field.lineIndex(),
                com.angel.flexbuddy.importcore.ConfidenceLevel.valueOf(field.level().name()));
    }

    static <T> ParsedField<T> fromCore(com.angel.flexbuddy.importcore.ParsedField<T> field) {
        return new ParsedField<>(field.value(), field.confidence(), field.lineIndex(),
                ConfidenceLevel.valueOf(field.level().name()));
    }

    static com.angel.flexbuddy.importcore.ParsedShiftData toCore(ParsedShiftData shift) {
        // Existing warnings are already localized. The caller retains them and appends newly translated warnings.
        return new com.angel.flexbuddy.importcore.ParsedShiftData(toCore(shift.station()), toCore(shift.date()),
                toCore(shift.startTime()), toCore(shift.endTime()), toCore(shift.basePay()), toCore(shift.tips()), List.of());
    }

    static ImportWarning fromCore(com.angel.flexbuddy.importcore.ImportWarning warning) {
        return new ImportWarning(warning.code(), WarningSeverity.valueOf(warning.severity().name()), warning.field(),
                Messages.current(warning.messageKey()));
    }

    static ParsedShiftData fromCore(com.angel.flexbuddy.importcore.ParsedShiftData shift) {
        return new ParsedShiftData(fromCore(shift.station()), fromCore(shift.date()), fromCore(shift.startTime()),
                fromCore(shift.endTime()), fromCore(shift.basePay()), fromCore(shift.tips()),
                shift.warnings().stream().map(ImportCoreAdapter::fromCore).toList());
    }
}
