package com.angel.flexbuddy.testserver;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * On the test server only, the offline page at its usual address is rendered with the banner, so the copy the service
 * worker keeps (sw.js caches /offline.html at install) still warns when the phone is disconnected. Elsewhere the static
 * static/offline.html is served unchanged.
 */
@Controller
@ConditionalOnProperty(name = TestServerConfig.PROPERTY, havingValue = "true")
public class TestServerOfflineController {

    @GetMapping("/offline.html")
    public String offline() {
        return "offline";
    }
}
