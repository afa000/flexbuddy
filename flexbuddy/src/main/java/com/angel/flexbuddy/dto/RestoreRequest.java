package com.angel.flexbuddy.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record RestoreRequest(
        @NotBlank String token,
        @NotNull RestoreMode mode,
        boolean includeDeleted,
        boolean acknowledgeReplace
) {
}
