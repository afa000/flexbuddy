package com.angel.flexbuddy.importcore;

public record ImportWarning(
        String code,
        WarningSeverity severity,
        String field,
        String messageKey
) {
}
