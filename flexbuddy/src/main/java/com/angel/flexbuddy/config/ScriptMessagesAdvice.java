package com.angel.flexbuddy.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import com.angel.flexbuddy.controller.AccountController;
import com.angel.flexbuddy.controller.EmailVerificationController;
import com.angel.flexbuddy.controller.PageController;
import com.angel.flexbuddy.controller.PasswordResetController;
import com.angel.flexbuddy.controller.TwoFactorController;
import com.angel.flexbuddy.i18n.ScriptMessages;

import tools.jackson.databind.ObjectMapper;

/** Gives the pages that load scripts the scripts' text, in the language of the request. */
@ControllerAdvice(assignableTypes = {PageController.class, AccountController.class, PasswordResetController.class,
        EmailVerificationController.class, TwoFactorController.class})
public class ScriptMessagesAdvice {

    private final ScriptMessages scriptMessages;

    public ScriptMessagesAdvice(MessageSource messages, ObjectMapper mapper) {
        try {
            this.scriptMessages = new ScriptMessages(messages, mapper);
        } catch (IOException exception) {
            throw new UncheckedIOException("The message file could not be read.", exception);
        }
    }

    @ModelAttribute("scriptMessages")
    public String scriptMessages(Locale locale) {
        return scriptMessages.json(locale);
    }
}
