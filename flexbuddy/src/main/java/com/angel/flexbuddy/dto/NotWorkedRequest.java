package com.angel.flexbuddy.dto;

import java.time.Instant;

import jakarta.validation.constraints.NotNull;

/**
 * Moves a block that was marked completed by mistake back to scheduled. It names the version of the block the driver
 * saw, so a block changed elsewhere since is never corrected blind.
 */
public record NotWorkedRequest(@NotNull Instant expectedUpdatedAt) {
}
