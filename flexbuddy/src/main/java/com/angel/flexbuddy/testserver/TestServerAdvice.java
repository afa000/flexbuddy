package com.angel.flexbuddy.testserver;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/**
 * Tells every page, error pages included, to show the test-server banner (templates/fragments/test-server.html). It
 * applies to all controllers because every full page must carry it; without this advice the attribute is absent and
 * the fragment renders nothing.
 */
@ControllerAdvice
@ConditionalOnProperty(name = TestServerConfig.PROPERTY, havingValue = "true")
public class TestServerAdvice {

    @ModelAttribute("testServer")
    public boolean testServer() {
        return true;
    }
}
