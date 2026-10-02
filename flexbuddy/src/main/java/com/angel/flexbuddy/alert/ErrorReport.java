package com.angel.flexbuddy.alert;

import java.util.List;

/**
 * What an alert says about one failure. It carries class names, code locations, the app's own log templates and, for a
 * page error, a scrubbed script message, the screen and the account number. It never carries an exception message or
 * anything a driver entered.
 */
public record ErrorReport(Source source, String kind, String where, List<String> details,
        Long accountId, String screen, String buildId) {

    public enum Source { SERVER, BROWSER }

    /** Same source, kind and place count as one error for throttling. */
    public String fingerprint() {
        return source + "|" + kind + "|" + where;
    }
}
