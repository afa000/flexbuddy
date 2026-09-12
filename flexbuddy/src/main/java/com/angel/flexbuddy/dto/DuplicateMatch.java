package com.angel.flexbuddy.dto;

public record DuplicateMatch(Long shiftId, DuplicateKind kind, String message) {
}
