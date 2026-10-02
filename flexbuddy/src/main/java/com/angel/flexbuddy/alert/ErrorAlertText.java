package com.angel.flexbuddy.alert;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/** The words of an alert email. It is for the operator, so times are UTC with the zone printed. */
public final class ErrorAlertText {

    private static final int MAX_SUBJECT = 150;
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private ErrorAlertText() {
    }

    public static String subject(ErrorReport report) {
        String subject;
        if (report.source() == ErrorReport.Source.BROWSER) {
            String file = report.where().contains(":") ? report.where().substring(0, report.where().indexOf(':')) : report.where();
            subject = "[FlexBuddy] Browser error: " + report.kind() + " in " + file;
        } else {
            String place = report.where().contains("(") ? report.where().substring(0, report.where().indexOf('(')) : report.where();
            subject = "[FlexBuddy] Server error: " + report.kind() + " at " + place;
        }
        return subject.length() > MAX_SUBJECT ? subject.substring(0, MAX_SUBJECT) : subject;
    }

    public static String body(ErrorReport report, Instant at, int repeatsSinceLastEmail) {
        StringBuilder body = new StringBuilder();
        body.append("When: ").append(WHEN.format(at)).append('\n');
        body.append("Version: ").append(report.buildId()).append('\n');
        body.append("Where: ").append(report.where()).append('\n');
        for (String detail : report.details()) {
            body.append(detail).append('\n');
        }
        if (report.source() == ErrorReport.Source.BROWSER) {
            body.append("Screen: ").append(report.screen()).append('\n');
            body.append("Account: #").append(report.accountId()).append('\n');
        }
        if (repeatsSinceLastEmail > 0) {
            body.append("Repeated ").append(repeatsSinceLastEmail).append(" more times since the last email\n");
        }
        body.append("\nSearch the Render logs around this time for the full trace.\n");
        return body.toString();
    }
}
