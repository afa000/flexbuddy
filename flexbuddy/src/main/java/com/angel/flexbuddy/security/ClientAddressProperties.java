package com.angel.flexbuddy.security;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Where the visitor's real address comes from behind Render's proxy. */
@ConfigurationProperties("flexbuddy.security.client-address")
public record ClientAddressProperties(
        /** Read this header instead of X-Forwarded-For, e.g. "CF-Connecting-IP"; blank uses X-Forwarded-For. */
        @DefaultValue("") String header,
        /** Proxies we trust that append to X-Forwarded-For; 1 means the right-most entry is the visitor. */
        @DefaultValue("1") int trustedHops) {
}
