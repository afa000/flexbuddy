package com.angel.flexbuddy.dto;

import com.angel.flexbuddy.validation.ValidTimeZone;

import jakarta.validation.constraints.NotBlank;

public record TimeZoneRequest(@NotBlank @ValidTimeZone String timeZone) {}
