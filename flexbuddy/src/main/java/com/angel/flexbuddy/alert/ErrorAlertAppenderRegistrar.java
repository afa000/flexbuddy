package com.angel.flexbuddy.alert;

import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;

/**
 * Attaches the alert appender to the root logger once the app is up, so startup errors, which the failed-deploy
 * notice and the uptime monitor cover, do not mail, and detaches it when the context closes. Test contexts share one
 * Logback root, so a context that did not detach would keep reporting other tests' errors.
 */
@Component
public class ErrorAlertAppenderRegistrar {

    private final ErrorAlertService service;
    private final ErrorAlertProperties properties;
    private final String buildId;
    private ErrorAlertAppender appender;

    public ErrorAlertAppenderRegistrar(ErrorAlertService service, ErrorAlertProperties properties,
            @Value("${flexbuddy.build-id:dev}") String buildId) {
        this.service = service;
        this.properties = properties;
        this.buildId = buildId == null || buildId.isBlank() || buildId.startsWith("@") ? "dev" : buildId;
    }

    @EventListener
    public synchronized void attach(ApplicationReadyEvent event) {
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (!properties.enabled() || appender != null || !(factory instanceof LoggerContext context)) {
            return;
        }
        appender = new ErrorAlertAppender(service, buildId);
        appender.setContext(context);
        appender.setName("errorAlerts");
        appender.start();
        context.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @EventListener
    public synchronized void detach(ContextClosedEvent event) {
        if (appender == null) {
            return;
        }
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext context) {
            context.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        }
        appender.stop();
        appender = null;
    }
}
