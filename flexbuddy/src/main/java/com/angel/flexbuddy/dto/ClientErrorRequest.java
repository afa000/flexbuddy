package com.angel.flexbuddy.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** An uncaught script error reported by one of the app's own pages. */
public record ClientErrorRequest(
        @NotBlank @Size(max = 300) String message,
        @Size(max = 300) String source,
        @Min(0) Integer line,
        @Min(0) Integer column,
        @Size(max = 20) String screen,
        @Size(max = 40) String buildId) {
}
