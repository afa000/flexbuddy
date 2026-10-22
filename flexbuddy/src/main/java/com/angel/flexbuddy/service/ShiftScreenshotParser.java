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

import com.angel.flexbuddy.i18n.Messages;

@Component
public class ShiftScreenshotParser {

    /** A station code always has a digit, which keeps the "Orders (Any)" filter from being read as one. */
    private static final Pattern STATION_PATTERN = Pattern.compile(
            "\\(([A-Z][A-Z0-9]{2,4})(?:/[A-Z0-9]+)?\\)", Pattern.CASE_INSENSITIVE);
    /** OCR can read the digit that ends a four-character code as a look-alike letter: VEA6 as VEAG, VBA5 as VBAS. */
    private static final Map<Character, Character> DIGIT_LOOKALIKES = Map.of(
            'G', '6', 'S', '5', 'O', '0', 'I', '1', 'L', '1', 'Z', '2', 'B', '8');
    private static final Pattern NUMERIC_DATE_PATTERN = Pattern.compile("(?<![\\d/])(\\d{1,2})/(\\d{1,2})(?![\\d/])");
    /** Pay in pounds is a British screenshot, which writes the day first like Spanish does. */
    private static final Pattern BRITISH_PATTERN = Pattern.compile("£|\\bGBP\\b");
    private static final Pattern NAMED_DATE_PATTERN = Pattern.compile(
            "\\b(?:Mon|Tue|Wed|Thu|Fri|Sat|Sun)[a-z]*,?\\s*" +
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
    /**
     * Flex leaves out ":00" on the hour, so 16:30 - 21, 19 - 22 and 5:30 p.m. - 8 p.m. are all ranges. Either side
     * may have minutes and a meridiem; a range where neither side has minutes needs a duration on its line.
     */
    private static final Pattern TIME_RANGE_PATTERN = Pattern.compile(
            "(?<![\\d:/.,$£€])([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)?\\s*[-–—~]\\s*"
                    + "([01]?\\d|2[0-3])(?::([0-5]\\d))?\\s*(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)?(?![\\d:/])",
            Pattern.CASE_INSENSITIVE);
    /** A start time and a dash with no end after it: a narrow card wrapped the end onto the next line. */
    private static final Pattern OPEN_RANGE_PATTERN = Pattern.compile(
            "(?<![\\d:/.,$£€])([01]?\\d|2[0-3]):[0-5]\\d\\s*(?:a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)?\\s*[-–—~](?!\\s*\\d)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern DURATION_PATTERN = Pattern.compile("\\d\\s*(?:hrs?|hora|min)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern TIME_TOKEN_PATTERN = Pattern.compile(
            "(?<![\\d:])([01]?\\d|2[0-3]):([0-5]\\d)\\s*(a\\.?\\s?m\\.?|p\\.?\\s?m\\.?)?(?![\\d:])", Pattern.CASE_INSENSITIVE);
    private static final Pattern START_LABEL_PATTERN = Pattern.compile("\\b(?:inicio|empieza|start|starts)\\b");
    private static final Pattern END_LABEL_PATTERN = Pattern.compile(
            "\\b(?:terminar en|termina|finalizar|fin|end|ends)\\b");
    /** $121.50, £47.50, MXN 397 and $1,000, or a code after the amount with a decimal comma: 51,00 GBP. */
    private static final Pattern PAY_PATTERN = Pattern.compile(
            "(?:[$£€]|\\b(?:MXN|GBP|EUR)(?![A-Za-z]))\\s*([0-9]+(?:,[0-9]{3})*(?:\\.[0-9]{1,2})?)"
                    + "|\\b([0-9]+(?:[.,][0-9]{2})?)\\s*(?:MXN|GBP|EUR)\\b");
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
        ParsedField<String> station = parseStation(safeLines);
        ParsedField<LocalDate> date = parseDate(safeLines, year);
        ParsedField<LocalTime>[] times = parseTimes(safeLines);
        ParsedField<BigDecimal> basePay = firstMatch(safeLines, PAY_PATTERN, matcher -> matcher.group(1) != null
                ? new BigDecimal(matcher.group(1).replace(",", ""))
                : new BigDecimal(matcher.group(2).replace(',', '.')));
        ParsedField<BigDecimal> tips = firstMatch(safeLines, TIPS_PATTERN,
                matcher -> new BigDecimal(matcher.group(1).replace(",", "")));
        if (tips.value() == null) tips = ParsedField.defaulted(BigDecimal.ZERO);

        if (date.value() != null && shouldUsePreviousYear(date.value(), context.today())) {
            date = date.withValue(date.value().minusYears(1));
            warnings.add(new ImportWarning("YEAR_ROLLOVER", WarningSeverity.INFO, "date",
                    Messages.current("import.warning.yearRollover")));
        }
        return new ParsedShiftData(station, date, times[0], times[1], basePay, tips, warnings);
    }

    /** True when the text contains a word that only Spanish uses. */
    static boolean looksSpanish(List<OcrLine> lines) {
        for (OcrLine line : lines) {
            for (String word : fold(line.text()).split("[^a-z0-9]+")) {
                if (SPANISH_WORDS.contains(word)) return true;
            }
        }
        return false;
    }

    private ParsedField<String> parseStation(List<OcrLine> lines) {
        for (OcrLine line : lines) {
            Matcher matcher = STATION_PATTERN.matcher(line.text());
            while (matcher.find()) {
                String code = matcher.group(1).toUpperCase(Locale.ROOT);
                Character digit = DIGIT_LOOKALIKES.get(code.charAt(code.length() - 1));
                if (code.length() == 4 && digit != null && !code.matches(".*\\d.*")) {
                    code = code.substring(0, 3) + digit;
                }
                if (code.matches(".*\\d.*")) return ParsedField.found(code, line);
            }
        }
        return ParsedField.missing();
    }

    private ParsedField<LocalDate> parseDate(List<OcrLine> lines, int year) {
        boolean spanish = looksSpanish(lines);
        boolean british = lines.stream().anyMatch(line -> BRITISH_PATTERN.matcher(line.text()).find());
        for (OcrLine line : lines) {
            Matcher numericDate = NUMERIC_DATE_PATTERN.matcher(line.text());
            if (numericDate.find()) {
                // Mexican and British Flex write the day first and US Spanish the month first, padded (07/28).
                boolean dayFirst = british || spanish && !numericDate.group(1).startsWith("0");
                LocalDate date = numericDate(year, numericDate.group(1), numericDate.group(2), dayFirst);
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
        for (int index = 0; index < lines.size(); index++) {
            OcrLine line = lines.get(index);
            String text = line.text();
            // A narrow offer card wraps "11:45 AM -" and "2:45 PM" onto two lines, often with the pay beside the first.
            Matcher open = OPEN_RANGE_PATTERN.matcher(text);
            if (open.find() && index + 1 < lines.size()) {
                text = text.substring(0, open.end()) + " " + lines.get(index + 1).text();
            }
            Matcher matcher = TIME_RANGE_PATTERN.matcher(text);
            while (matcher.find()) {
                boolean bareHours = matcher.group(2) == null && matcher.group(5) == null
                        && matcher.group(3) == null && matcher.group(6) == null;
                if (bareHours && !DURATION_PATTERN.matcher(text).find()) continue;
                LocalTime start = toLocalTime(matcher.group(1), matcher.group(2), matcher.group(3));
                LocalTime end = toLocalTime(matcher.group(4), matcher.group(5), matcher.group(6));
                return new ParsedField[] {ParsedField.found(start, line), ParsedField.found(end, line)};
            }
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

    private LocalTime toLocalTime(Matcher time) {
        return toLocalTime(time.group(1), time.group(2), time.group(3));
    }

    /** 24-hour times ignore a meridiem; otherwise p.m. adds twelve to 1–11 and 12 a.m. is midnight. */
    private LocalTime toLocalTime(String hours, String minutes, String meridiem) {
        int hour = Integer.parseInt(hours);
        int minute = minutes == null ? 0 : Integer.parseInt(minutes);
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

    /** The preferred order first; when that is not a real date, the other one (27/02 can only be day first). */
    private LocalDate numericDate(int year, String first, String second, boolean dayFirst) {
        int a = Integer.parseInt(first);
        int b = Integer.parseInt(second);
        LocalDate preferred = dayFirst ? createDate(year, b, a) : createDate(year, a, b);
        if (preferred != null) return preferred;
        return dayFirst ? createDate(year, a, b) : createDate(year, b, a);
    }

    private LocalDate createDate(int year, int month, int day) {
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException exception) {
            return null;
        }
    }
}
