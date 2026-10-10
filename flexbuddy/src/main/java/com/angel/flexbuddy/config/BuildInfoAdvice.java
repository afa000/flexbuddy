package com.angel.flexbuddy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.angel.flexbuddy.controller.AccountController;
import com.angel.flexbuddy.controller.EmailVerificationController;
import com.angel.flexbuddy.controller.PageController;
import com.angel.flexbuddy.controller.PasswordResetController;
import com.angel.flexbuddy.controller.TwoFactorController;

/**
 * Exposes the build id to templates so asset URLs carry the same version the service worker caches under.
 * Maven filters the id into application.properties; unfiltered runs (an IDE launch) fall back to "dev".
 */
@ControllerAdvice(assignableTypes = {PageController.class, AccountController.class, PasswordResetController.class,
        EmailVerificationController.class, TwoFactorController.class})
public class BuildInfoAdvice {

    private final String buildId;

    public BuildInfoAdvice(@Value("${flexbuddy.build-id:dev}") String buildId) {
        this.buildId = buildId == null || buildId.isBlank() || buildId.startsWith("@") ? "dev" : buildId;
    }

    @ModelAttribute("buildId")
    public String buildId() {
        return buildId;
    }
}
