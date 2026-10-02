package com.angel.flexbuddy.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import com.angel.flexbuddy.dto.ClientErrorRequest;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;

/**
 * Turns a log event or a page error into an {@link ErrorReport}. The rule that matters: never read a formatted log
 * message, an exception's message, MDC values or log arguments, because those can hold emails, SQL values or tokens.
 */
public final class ErrorReports {

    private static final String APP_PACKAGE = "com.angel.flexbuddy";
    private static final int MAX_APP_FRAMES = 8;
    private static final int MAX_FALLBACK_FRAMES = 3;
    private static final int MAX_CAUSES = 5;
    private static final int MAX_MESSAGE = 300;
    private static final Pattern SCRIPT_PATH = Pattern.compile("^/js/[a-z0-9-]+\\.js$");
    private static final Pattern ORIGIN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9+.-]*://[^/]*");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern LONG_TOKEN = Pattern.compile("[A-Za-z0-9_-]{16,}");
    private static final Pattern ERROR_NAME = Pattern.compile("^[A-Za-z]*Error$");
    private static final Pattern BUILD_ID = Pattern.compile("^[0-9A-Za-z.:-]{1,40}$");
    private static final Set<String> SCREENS = Set.of("home", "reports", "schedule", "import", "expenses", "account");

    private ErrorReports() {
    }

    public static ErrorReport fromLogEvent(ILoggingEvent event, String buildId) {
        String logger = simpleName(event.getLoggerName());
        IThrowableProxy top = event.getThrowableProxy();
        if (top == null) {
            List<String> details = event.getLoggerName() != null && event.getLoggerName().startsWith(APP_PACKAGE)
                    && event.getMessage() != null ? List.of(event.getMessage()) : List.of();
            return new ErrorReport(ErrorReport.Source.SERVER, "Logged error", logger, details, null, null, buildId);
        }

        List<IThrowableProxy> chain = new ArrayList<>();
        for (IThrowableProxy cause = top; cause != null && chain.size() < 20; cause = cause.getCause()) {
            chain.add(cause);
        }
        List<String> details = new ArrayList<>();
        for (int i = 1; i < chain.size() && i <= MAX_CAUSES; i++) {
            details.add("caused by " + simpleName(chain.get(i).getClassName()));
        }

        // The trace of the deepest cause that has a frame in this app says where it went wrong.
        List<String> frames = List.of();
        for (int i = chain.size() - 1; i >= 0 && frames.isEmpty(); i--) {
            frames = appFrames(chain.get(i));
        }
        String where = logger;
        if (!frames.isEmpty()) {
            where = frames.get(0);
        } else {
            frames = firstFrames(chain.get(chain.size() - 1));
        }
        details.addAll(frames);
        return new ErrorReport(ErrorReport.Source.SERVER, simpleName(top.getClassName()), where, details, null, null, buildId);
    }

    public static ErrorReport fromBrowser(ClientErrorRequest request, long accountId) {
        String source = scriptPath(request.source());
        int line = request.line() == null ? 0 : request.line();
        int column = request.column() == null ? 0 : request.column();
        String message = scrub(request.message());
        return new ErrorReport(ErrorReport.Source.BROWSER, browserKind(request.message()), source + ":" + line + ":" + column,
                List.of(message), accountId, screen(request.screen()), buildId(request.buildId()));
    }

    private static List<String> appFrames(IThrowableProxy throwable) {
        List<String> frames = new ArrayList<>();
        StackTraceElementProxy[] trace = throwable.getStackTraceElementProxyArray();
        if (trace == null) {
            return frames;
        }
        for (StackTraceElementProxy proxy : trace) {
            StackTraceElement element = proxy.getStackTraceElement();
            if (element.getClassName().startsWith(APP_PACKAGE) && frames.size() < MAX_APP_FRAMES) {
                frames.add(frame(element));
            }
        }
        return frames;
    }

    private static List<String> firstFrames(IThrowableProxy throwable) {
        List<String> frames = new ArrayList<>();
        StackTraceElementProxy[] trace = throwable.getStackTraceElementProxyArray();
        if (trace != null) {
            for (int i = 0; i < trace.length && i < MAX_FALLBACK_FRAMES; i++) {
                frames.add(frame(trace[i].getStackTraceElement()));
            }
        }
        return frames;
    }

    /** "ShiftService.update(ShiftService.java:212)": a class, a method and a line, never an argument. */
    private static String frame(StackTraceElement element) {
        return simpleName(element.getClassName()) + "." + element.getMethodName()
                + "(" + element.getFileName() + ":" + element.getLineNumber() + ")";
    }

    private static String simpleName(String name) {
        if (name == null || name.isBlank()) {
            return "unknown";
        }
        return name.substring(name.lastIndexOf('.') + 1);
    }

    /** The script's path under /js/, or "page" for anything else, such as a file from another site. */
    private static String scriptPath(String source) {
        if (source == null) {
            return "page";
        }
        String path = ORIGIN.matcher(source).replaceFirst("");
        int cut = path.indexOf('?');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        cut = path.indexOf('#');
        if (cut >= 0) {
            path = path.substring(0, cut);
        }
        return SCRIPT_PATH.matcher(path).matches() ? path : "page";
    }

    private static String browserKind(String rawMessage) {
        String message = rawMessage.strip();
        if (message.startsWith("Uncaught ")) {
            message = message.substring("Uncaught ".length());
        }
        int colon = message.indexOf(':');
        if (colon > 0) {
            String name = message.substring(0, colon);
            if (ERROR_NAME.matcher(name).matches()) {
                return name;
            }
        }
        return "Error";
    }

    /** Whitespace collapsed, emails and long token-like runs replaced, and the result cut to 300 characters. */
    static String scrub(String message) {
        String clean = message.replaceAll("\\s+", " ").strip();
        clean = EMAIL.matcher(clean).replaceAll("[email]");
        clean = LONG_TOKEN.matcher(clean).replaceAll("[id]");
        return clean.length() > MAX_MESSAGE ? clean.substring(0, MAX_MESSAGE) : clean;
    }

    private static String screen(String screen) {
        if ("dashboard".equals(screen)) {
            return "home";
        }
        return screen != null && SCREENS.contains(screen) ? screen : "other";
    }

    private static String buildId(String buildId) {
        return buildId != null && BUILD_ID.matcher(buildId).matches() ? buildId : "unknown";
    }
}
