package com.angel.flexbuddy.service;

public record ImportWarning(
        String code,
        WarningSeverity severity,
        String field,
        String message
) {
}
