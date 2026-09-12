package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

@Component
public class ShiftScreenshotParser {

    private static final Pattern STATION_PATTERN = Pattern.compile(
            "\\(([A-Z][A-Z0-9]{2,})(?:/[A-Z0-9]+)?\\)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMERIC_DATE_PATTERN = Pattern.compile(
            "\\b(1[0-2]|0?[1-9])/(3[01]|[12]?[0-9])\\b");
    private static final Pattern NAMED_DATE_PATTERN = Pattern.compile(
            "\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*,\\s*" +
                    "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+" +
                    "(3[01]|[12]?[0-9])\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
            "\\b([01]?\\d|2[0-3]):([0-5]\\d)\\s*-\\s*([01]?\\d|2[0-3]):([0-5]\\d)\\b");
    private static final Pattern PAY_PATTERN = Pattern.compile(
            "\\$\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)");
    private static final Pattern TIPS_PATTERN = Pattern.compile(
            "^\\s*Tips\\s*\\$\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("jan", Month.JANUARY), Map.entry("feb", Month.FEBRUARY),
            Map.entry("mar", Month.MARCH), Map.entry("apr", Month.APRIL),
            Map.entry("may", Month.MAY), Map.entry("jun", Month.JUNE),
            Map.entry("jul", Month.JULY), Map.entry("aug", Month.AUGUST),
            Map.entry("sep", Month.SEPTEMBER), Map.entry("oct", Month.OCTOBER),
            Map.entry("nov", Month.NOVEMBER), Map.entry("dec", Month.DECEMBER));

    public ParsedShiftData parse(String rawText, int year) {
        List<String> textLines = Arrays.stream((rawText == null ? "" : rawText).split("\\R"))
                .map(String::trim).filter(line -> !line.isBlank()).toList();
        List<OcrLine> lines = new ArrayList<>();
        for (int index = 0; index < textLines.size(); index++) {
            lines.add(new OcrLine(textLines.get(index), 100, 0, 0, 0, 0, index));
        }
        ParseContext context = new ParseContext(LocalDate.now(), null);
        return new ImportWarningRules().apply(parse(lines, year, context), 100, context);
    }

    public ParsedShiftData parse(List<OcrLine> lines, int year, ParseContext context) {
        List<OcrLine> safeLines = lines == null ? List.of() : lines;
        List<ImportWarning> warnings = new ArrayList<>();
        ParsedField<String> station = firstMatch(safeLines, STATION_PATTERN,
                matcher -> matcher.group(1).toUpperCase(Locale.ROOT));
        ParsedField<LocalDate> date = parseDate(safeLines, year);
        ParsedField<LocalTime>[] times = parseTimes(safeLines);
        ParsedField<BigDecimal> basePay = firstMatch(safeLines, PAY_PATTERN,
                matcher -> new BigDecimal(matcher.group(1).replace(",", "")));
        ParsedField<BigDecimal> tips = firstMatch(safeLines, TIPS_PATTERN,
                matcher -> new BigDecimal(matcher.group(1).replace(",", "")));
        if (tips.value() == null) tips = ParsedField.defaulted(BigDecimal.ZERO);

        if (date.value() != null && shouldUsePreviousYear(date.value(), context.today())) {
            date = date.withValue(date.value().minusYears(1));
            warnings.add(new ImportWarning("YEAR_ROLLOVER", WarningSeverity.INFO, "date",
                    "The screenshot does not include a year, so the date was adjusted to the previous year."));
        }
        return new ParsedShiftData(station, date, times[0], times[1], basePay, tips, warnings);
    }

    private ParsedField<LocalDate> parseDate(List<OcrLine> lines, int year) {
        for (OcrLine line : lines) {
            Matcher numericDate = NUMERIC_DATE_PATTERN.matcher(line.text());
            if (numericDate.find()) {
                LocalDate date = createDate(year, Integer.parseInt(numericDate.group(1)), Integer.parseInt(numericDate.group(2)));
                if (date != null) return ParsedField.found(date, line);
            }
            Matcher namedDate = NAMED_DATE_PATTERN.matcher(line.text());
            if (namedDate.find()) {
                Month month = MONTHS.get(namedDate.group(1).substring(0, 3).toLowerCase(Locale.ROOT));
                LocalDate date = createDate(year, month.getValue(), Integer.parseInt(namedDate.group(2)));
                if (date != null) return ParsedField.found(date, line);
            }
        }
        return ParsedField.missing();
    }

    @SuppressWarnings("unchecked")
    private ParsedField<LocalTime>[] parseTimes(List<OcrLine> lines) {
        for (OcrLine line : lines) {
            Matcher matcher = TIME_RANGE_PATTERN.matcher(line.text());
            if (!matcher.find()) continue;
            LocalTime start = LocalTime.of(Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
            LocalTime end = LocalTime.of(Integer.parseInt(matcher.group(3)), Integer.parseInt(matcher.group(4)));
            return new ParsedField[] {ParsedField.found(start, line), ParsedField.found(end, line)};
        }
        return new ParsedField[] {ParsedField.missing(), ParsedField.missing()};
    }

    private <T> ParsedField<T> firstMatch(List<OcrLine> lines, Pattern pattern, Function<Matcher, T> mapper) {
        for (OcrLine line : lines) {
            Matcher matcher = pattern.matcher(line.text());
            if (matcher.find()) return ParsedField.found(mapper.apply(matcher), line);
        }
        return ParsedField.missing();
    }

    private boolean shouldUsePreviousYear(LocalDate parsedDate, LocalDate today) {
        return parsedDate.isAfter(today.plusDays(14))
                && today.getMonthValue() <= 2
                && parsedDate.getMonthValue() >= 11;
    }

    private LocalDate createDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException exception) {
            return null;
        }
    }
}
