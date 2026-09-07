package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class ShiftScreenshotParser {

    private static final Pattern STATION_PATTERN = Pattern.compile(
            "\\(([A-Z][A-Z0-9]{2,})(?:/[A-Z0-9]+)?\\)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern NUMERIC_DATE_PATTERN = Pattern.compile(
            "\\b(1[0-2]|0?[1-9])/(3[01]|[12]?[0-9])\\b"
    );
    private static final Pattern NAMED_DATE_PATTERN = Pattern.compile(
            "\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*,\\s*" +
                    "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+" +
                    "(3[01]|[12]?[0-9])\\b",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
            "\\b([01]?\\d|2[0-3]):([0-5]\\d)\\s*-\\s*" +
                    "([01]?\\d|2[0-3]):([0-5]\\d)\\b"
    );
    private static final Pattern PAY_PATTERN = Pattern.compile(
            "\\$\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)"
    );
    private static final Pattern TIPS_PATTERN = Pattern.compile(
            "^\\s*Tips\\s*\\$\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)",
            Pattern.CASE_INSENSITIVE | Pattern.MULTILINE
    );
    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("jan", Month.JANUARY),
            Map.entry("feb", Month.FEBRUARY),
            Map.entry("mar", Month.MARCH),
            Map.entry("apr", Month.APRIL),
            Map.entry("may", Month.MAY),
            Map.entry("jun", Month.JUNE),
            Map.entry("jul", Month.JULY),
            Map.entry("aug", Month.AUGUST),
            Map.entry("sep", Month.SEPTEMBER),
            Map.entry("oct", Month.OCTOBER),
            Map.entry("nov", Month.NOVEMBER),
            Map.entry("dec", Month.DECEMBER)
    );

    public ParsedShiftData parse(String rawText, int year) {
        String text = rawText == null ? "" : rawText;
        List<String> warnings = new ArrayList<>();

        String station = parseStation(text);
        LocalDate date = parseDate(text, year);
        LocalTime[] times = parseTimes(text);
        BigDecimal basePay = parseBasePay(text);
        BigDecimal tips = parseTips(text);

        addWarningIfMissing(station, "Station could not be read from the screenshot.", warnings);
        addWarningIfMissing(date, "Date could not be read from the screenshot.", warnings);
        addWarningIfMissing(times[0], "Start time could not be read from the screenshot.", warnings);
        addWarningIfMissing(times[1], "End time could not be read from the screenshot.", warnings);
        addWarningIfMissing(basePay, "Base pay could not be read from the screenshot.", warnings);

        return new ParsedShiftData(
                station,
                date,
                times[0],
                times[1],
                basePay,
                tips,
                warnings
        );
    }

    private String parseStation(String text) {
        Matcher matcher = STATION_PATTERN.matcher(text);
        return matcher.find() ? matcher.group(1).toUpperCase(Locale.ROOT) : null;
    }

    private LocalDate parseDate(String text, int year) {
        Matcher numericDate = NUMERIC_DATE_PATTERN.matcher(text);
        if (numericDate.find()) {
            return createDate(
                    year,
                    Integer.parseInt(numericDate.group(1)),
                    Integer.parseInt(numericDate.group(2))
            );
        }

        Matcher namedDate = NAMED_DATE_PATTERN.matcher(text);
        if (namedDate.find()) {
            Month month = MONTHS.get(
                    namedDate.group(1).substring(0, 3).toLowerCase(Locale.ROOT)
            );
            return createDate(year, month.getValue(), Integer.parseInt(namedDate.group(2)));
        }

        return null;
    }

    private LocalDate createDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        }
        catch (DateTimeException exception) {
            return null;
        }
    }

    private LocalTime[] parseTimes(String text) {
        Matcher matcher = TIME_RANGE_PATTERN.matcher(text);
        if (!matcher.find()) {
            return new LocalTime[] {null, null};
        }

        LocalTime startTime = LocalTime.of(
                Integer.parseInt(matcher.group(1)),
                Integer.parseInt(matcher.group(2))
        );
        LocalTime endTime = LocalTime.of(
                Integer.parseInt(matcher.group(3)),
                Integer.parseInt(matcher.group(4))
        );
        return new LocalTime[] {startTime, endTime};
    }

    private BigDecimal parseBasePay(String text) {
        Matcher matcher = PAY_PATTERN.matcher(text);
        if (!matcher.find()) {
            return null;
        }

        return new BigDecimal(matcher.group(1).replace(",", ""));
    }

    private BigDecimal parseTips(String text) {
        Matcher matcher = TIPS_PATTERN.matcher(text);
        if (!matcher.find()) {
            return BigDecimal.ZERO;
        }

        return new BigDecimal(matcher.group(1).replace(",", ""));
    }

    private void addWarningIfMissing(Object value, String warning, List<String> warnings) {
        if (value == null) {
            warnings.add(warning);
        }
    }
}
