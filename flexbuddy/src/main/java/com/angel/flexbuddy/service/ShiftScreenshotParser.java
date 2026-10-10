package com.angel.flexbuddy.service;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Month;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
    /** Spanish writes the day first, so 6/9 is the sixth of September. */
    private static final Pattern NUMERIC_DATE_DAY_FIRST_PATTERN = Pattern.compile(
            "\\b(3[01]|[12]?[0-9])/(1[0-2]|0?[1-9])\\b");
    private static final Pattern NAMED_DATE_PATTERN = Pattern.compile(
            "\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*,\\s*" +
                    "(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+" +
                    "(3[01]|[12]?[0-9])\\b", Pattern.CASE_INSENSITIVE);
    /**
     * A day, then optionally "de" and a weekday word, then the month: 13 de octubre, 13 MARTES OCTUBRE, mar., 13 oct.
     * The weekday is any word of four to ten characters, because English OCR turns "miércoles" and "sábado" into
     * variants. The boundary after each month keeps MARTES from being read as the month "mar".
     */
    private static final Pattern SPANISH_DATE_PATTERN = Pattern.compile(
            "\\b(3[01]|[12]?[0-9])\\s+(?:de\\s+)?(?:[a-z0-9]{4,10}\\.?,?\\s+)?(?:de\\s+)?"
                    + "(ene(?:ro)?|feb(?:rero)?|mar(?:zo)?|abr(?:il)?|may(?:o)?|jun(?:io)?|jul(?:io)?|ago(?:sto)?"
                    + "|sep(?:t(?:iembre)?)?|set(?:iembre)?|oct(?:ubre)?|nov(?:iembre)?|dic(?:iembre)?)\\.?\\b");
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
            "\\b([01]?\\d|2[0-3]):([0-5]\\d)\\s*-\\s*([01]?\\d|2[0-3]):([0-5]\\d)\\b");
    private static final Pattern TIME_TOKEN_PATTERN = Pattern.compile(
            "(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)\\s*(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)?(?![\\d:])", Pattern.CASE_INSENSITIVE);
    private static final Pattern START_LABEL_PATTERN = Pattern.compile("\\b(?:inicio|empieza|start|starts)\\b");
    private static final Pattern END_LABEL_PATTERN = Pattern.compile("\\b(?:terminar en|termina|fin|end|ends)\\b");
    private static final Pattern PAY_PATTERN = Pattern.compile(
            "\\$\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)");
    private static final Pattern TIPS_PATTERN = Pattern.compile(
            "^\\s*(?:Tips|Propinas)\\s*:?\\s*\\$\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)", Pattern.CASE_INSENSITIVE);
    private static final Map<String, Month> MONTHS = Map.ofEntries(
            Map.entry("jan", Month.JANUARY), Map.entry("feb", Month.FEBRUARY),
            Map.entry("mar", Month.MARCH), Map.entry("apr", Month.APRIL),
            Map.entry("may", Month.MAY), Map.entry("jun", Month.JUNE),
            Map.entry("jul", Month.JULY), Map.entry("aug", Month.AUGUST),
            Map.entry("sep", Month.SEPTEMBER), Map.entry("oct", Month.OCTOBER),
            Map.entry("nov", Month.NOVEMBER), Map.entry("dec", Month.DECEMBER));
    private static final Map<String, Month> SPANISH_MONTHS = Map.ofEntries(
            Map.entry("ene", Month.JANUARY), Map.entry("feb", Month.FEBRUARY),
            Map.entry("mar", Month.MARCH), Map.entry("abr", Month.APRIL),
            Map.entry("may", Month.MAY), Map.entry("jun", Month.JUNE),
            Map.entry("jul", Month.JULY), Map.entry("ago", Month.AUGUST),
            Map.entry("sep", Month.SEPTEMBER), Map.entry("set", Month.SEPTEMBER),
            Map.entry("oct", Month.OCTOBER), Map.entry("nov", Month.NOVEMBER),
            Map.entry("dic", Month.DECEMBER));
    /** Whole words that only a Spanish screenshot contains, once accents are stripped. */
    private static final Set<String> SPANISH_WORDS = Set.of(
            "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "setiembre",
            "octubre", "noviembre", "diciembre",
            "lunes", "martes", "miercoles", "jueves", "viernes", "sabado", "domingo",
            "inicio", "terminar", "propinas", "programacion", "ubicacion", "actualizaciones",
            // How English OCR reads "Programación".
            "programaci6n");

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

    /** True when the text contains a word that only Spanish uses, which also means a numeric date is day first. */
    static boolean looksSpanish(List<OcrLine> lines) {
        for (OcrLine line : lines) {
            for (String word : fold(line.text()).split("[^a-z0-9]+")) {
                if (SPANISH_WORDS.contains(word)) return true;
            }
        }
        return false;
    }

    private ParsedField<LocalDate> parseDate(List<OcrLine> lines, int year) {
        Pattern numeric = looksSpanish(lines) ? NUMERIC_DATE_DAY_FIRST_PATTERN : NUMERIC_DATE_PATTERN;
        boolean dayFirst = numeric == NUMERIC_DATE_DAY_FIRST_PATTERN;
        for (OcrLine line : lines) {
            Matcher numericDate = numeric.matcher(line.text());
            if (numericDate.find()) {
                int first = Integer.parseInt(numericDate.group(1));
                int second = Integer.parseInt(numericDate.group(2));
                LocalDate date = dayFirst ? createDate(year, second, first) : createDate(year, first, second);
                if (date != null) return ParsedField.found(date, line);
            }
            Matcher namedDate = NAMED_DATE_PATTERN.matcher(line.text());
            if (namedDate.find()) {
                Month month = MONTHS.get(namedDate.group(1).substring(0, 3).toLowerCase(Locale.ROOT));
                LocalDate date = createDate(year, month.getValue(), Integer.parseInt(namedDate.group(2)));
                if (date != null) return ParsedField.found(date, line);
            }
        }
        for (OcrLine line : lines) {
            Matcher spanishDate = SPANISH_DATE_PATTERN.matcher(fold(line.text()));
            if (spanishDate.find()) {
                Month month = SPANISH_MONTHS.get(spanishDate.group(2).substring(0, 3));
                LocalDate date = createDate(year, month.getValue(), Integer.parseInt(spanishDate.group(1)));
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
        // Flex also shows the start and end under their own labels, in either language.
        ParsedField<LocalTime> start = labelledTime(lines, START_LABEL_PATTERN);
        ParsedField<LocalTime> end = labelledTime(lines, END_LABEL_PATTERN);
        if (start.value() == null && end.value() == null) {
            List<ParsedField<LocalTime>> all = new ArrayList<>();
            for (OcrLine line : lines) {
                Matcher matcher = TIME_TOKEN_PATTERN.matcher(line.text());
                while (matcher.find()) all.add(ParsedField.found(toLocalTime(matcher), line));
            }
            if (all.size() == 2) return new ParsedField[] {all.get(0), all.get(1)};
        }
        return new ParsedField[] {start, end};
    }

    /** The first time after the label, on its own line or one of the next two. */
    private ParsedField<LocalTime> labelledTime(List<OcrLine> lines, Pattern label) {
        for (int index = 0; index < lines.size(); index++) {
            Matcher labelMatch = label.matcher(fold(lines.get(index).text()));
            if (!labelMatch.find()) continue;
            for (int offset = 0; offset <= 2 && index + offset < lines.size(); offset++) {
                OcrLine line = lines.get(index + offset);
                String text = offset == 0 ? line.text().substring(Math.min(labelMatch.end(), line.text().length())) : line.text();
                Matcher time = TIME_TOKEN_PATTERN.matcher(text);
                if (time.find()) return ParsedField.found(toLocalTime(time), line);
            }
        }
        return ParsedField.missing();
    }

    /** 24-hour times ignore a meridiem; otherwise p.m. adds twelve to 1–11 and 12 a.m. is midnight. */
    private LocalTime toLocalTime(Matcher time) {
        int hour = Integer.parseInt(time.group(1));
        int minute = Integer.parseInt(time.group(2));
        String meridiem = time.group(3);
        if (meridiem != null && hour <= 12) {
            boolean pm = Character.toLowerCase(meridiem.charAt(0)) == 'p';
            if (pm && hour < 12) hour += 12;
            if (!pm && hour == 12) hour = 0;
        }
        return LocalTime.of(hour, minute);
    }

    /** Lower case with accents removed, the form English OCR tends to return for Spanish text. */
    private static String fold(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
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
