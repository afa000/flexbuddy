package com.angel.flexbuddy.service;

import java.util.stream.Stream;

import org.springframework.stereotype.Component;

@Component
public class ImportWarningRules {
    private final com.angel.flexbuddy.importcore.ImportWarningRules rules =
            new com.angel.flexbuddy.importcore.ImportWarningRules();

    public ParsedShiftData apply(ParsedShiftData shift, int meanConfidence, ParseContext context) {
        return apply(shift, meanConfidence, context, false);
    }

    public ParsedShiftData apply(ParsedShiftData shift, int meanConfidence, ParseContext context, boolean scheduled) {
        var result = rules.apply(ImportCoreAdapter.toCore(shift), meanConfidence, ImportCoreAdapter.toCore(context), scheduled);
        return shift.withWarnings(Stream.concat(shift.warnings().stream(),
                result.warnings().stream().map(ImportCoreAdapter::fromCore)).distinct().toList());
    }
}
