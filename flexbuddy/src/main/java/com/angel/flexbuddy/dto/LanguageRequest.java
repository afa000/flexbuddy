package com.angel.flexbuddy.dto;

import jakarta.validation.constraints.Pattern;

/** The language a driver chose in Account: "en" or "es", or null to follow the device. */
public record LanguageRequest(@Pattern(regexp = "en|es", message = "{validation.language.invalid}") String language) {}
