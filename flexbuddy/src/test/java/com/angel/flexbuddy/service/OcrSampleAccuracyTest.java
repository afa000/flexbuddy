package com.angel.flexbuddy.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/**
 * Measures how often the screenshot import reads each field right on a folder of real Flex screenshots. Those
 * screenshots belong to drivers, so they and their expected values stay outside the repository:
 * FLEXBUDDY_OCR_SAMPLES names the folder, and FLEXBUDDY_OCR_LABELS a CSV of
 * {@code file,station,date(MM-dd),start(HH:mm),end(HH:mm),pay}, where an empty value is not checked and a line
 * starting with # is a comment. The report goes to target/ocr-samples/.
 */
@Tag("ocr-samples")
@EnabledIfEnvironmentVariable(named = "FLEXBUDDY_OCR_SAMPLES", matches = ".+")
class OcrSampleAccuracyTest {

    private static final String[] FIELDS = {"station", "date", "start", "end", "pay"};
    private static final DateTimeFormatter MONTH_DAY = DateTimeFormatter.ofPattern("MM-dd");

    private final ScreenshotPreprocessor preprocessor = new ScreenshotPreprocessor();
    private final TesseractScreenshotTextExtractor extractor = new TesseractScreenshotTextExtractor(preprocessor);
    private final ShiftScreenshotParser parser = new ShiftScreenshotParser();

    @Test
    void reportsFieldAccuracyOnTheSampleScreenshots() throws IOException {
        Path samples = Path.of(System.getenv("FLEXBUDDY_OCR_SAMPLES"));
        String labelsPath = System.getenv("FLEXBUDDY_OCR_LABELS");
        Map<String, String[]> labels = labelsPath == null ? Map.of() : readLabels(Path.of(labelsPath));
        String only = System.getenv("FLEXBUDDY_OCR_ONLY");
        Path output = Path.of("target", "ocr-samples");
        Files.createDirectories(output.resolve("text"));

        List<Path> images;
        try (Stream<Path> files = Files.list(samples)) {
            images = files.filter(file -> file.getFileName().toString().toLowerCase(Locale.ROOT).matches(".*\\.(png|jpe?g)"))
                    .filter(file -> only == null || file.getFileName().toString().matches(only))
                    .sorted().toList();
        }

        int[] checked = new int[FIELDS.length];
        int[] correct = new int[FIELDS.length];
        List<String> report = new ArrayList<>();
        report.add("file,field,expected,actual,ok");
        ParseContext context = new ParseContext(LocalDate.of(2026, 10, 21), null);
        for (Path image : images) {
            String name = image.getFileName().toString();
            String[] expected = labels.get(name);
            if (!labels.isEmpty() && expected == null) continue;

            OcrResult ocr = extractor.extract(read(image));
            Files.writeString(output.resolve("text").resolve(name + ".txt"), ocr.text(), StandardCharsets.UTF_8);
            ParsedShiftData parsed = parser.parse(ocr.lines(), 2026, context);
            String[] actual = {
                    parsed.station().value(),
                    parsed.date().value() == null ? null : parsed.date().value().format(MONTH_DAY),
                    Objects.toString(parsed.startTime().value(), null),
                    Objects.toString(parsed.endTime().value(), null),
                    parsed.basePay().value() == null ? null : plain(parsed.basePay().value())
            };
            for (int field = 0; field < FIELDS.length; field++) {
                String want = expected == null || expected.length <= field + 1 ? "" : expected[field + 1];
                if (want.isEmpty()) {
                    report.add(String.join(",", name, FIELDS[field], "", Objects.toString(actual[field], ""), ""));
                    continue;
                }
                boolean ok = field == 4
                        ? actual[field] != null && new BigDecimal(want).compareTo(new BigDecimal(actual[field])) == 0
                        : want.equals(actual[field]);
                checked[field]++;
                if (ok) correct[field]++;
                report.add(String.join(",", name, FIELDS[field], want, Objects.toString(actual[field], ""), ok ? "yes" : "NO"));
            }
        }
        Files.write(output.resolve("report.csv"), report, StandardCharsets.UTF_8);

        StringBuilder summary = new StringBuilder("OCR sample accuracy over " + images.size() + " images:");
        int allChecked = 0;
        int allCorrect = 0;
        for (int field = 0; field < FIELDS.length; field++) {
            summary.append(String.format(Locale.ROOT, "%n  %-8s %3d / %3d", FIELDS[field], correct[field], checked[field]));
            allChecked += checked[field];
            allCorrect += correct[field];
        }
        summary.append(String.format(Locale.ROOT, "%n  %-8s %3d / %3d", "all", allCorrect, allChecked));
        Files.writeString(output.resolve("summary.txt"), summary, StandardCharsets.UTF_8);
        System.out.println(summary);
    }

    private static String plain(BigDecimal value) {
        return value.stripTrailingZeros().toPlainString();
    }

    /** Read the way ShiftImportService reads an upload, subsampling anything over its decode limit. */
    private static BufferedImage read(Path image) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(image.toFile())) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input);
                ImageReadParam param = reader.getDefaultReadParam();
                int factor = ShiftImportService.subsampleFactor(reader.getWidth(0), reader.getHeight(0));
                if (factor > 1) param.setSourceSubsampling(factor, factor, 0, 0);
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        }
    }

    private static Map<String, String[]> readLabels(Path file) throws IOException {
        Map<String, String[]> labels = new LinkedHashMap<>();
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank() || line.startsWith("#") || line.startsWith("file,")) continue;
            String[] columns = line.split(",", -1);
            for (int index = 0; index < columns.length; index++) columns[index] = columns[index].trim();
            labels.put(columns[0], columns);
        }
        return labels;
    }
}
