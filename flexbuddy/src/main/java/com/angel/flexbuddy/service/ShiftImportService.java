package com.angel.flexbuddy.service;

import java.io.IOException;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.angel.flexbuddy.dto.DuplicateMatch;
import com.angel.flexbuddy.dto.ShiftImportPreviewResponse;
import com.angel.flexbuddy.dto.ShiftCandidate;
import com.angel.flexbuddy.exception.InvalidScreenshotException;
import com.angel.flexbuddy.model.ShiftStatus;

@Service
public class ShiftImportService {

    private static final int MAX_SIDE = 12_000;

    private final ScreenshotTextExtractor textExtractor;
    private final ShiftScreenshotParser screenshotParser;
    private final ImportWarningRules warningRules;
    private final ScheduledShiftMatcher scheduledShiftMatcher;
    private final UserTimeService userTime;
    private final Clock clock;
    private final long maxPixels;

    public ShiftImportService(
            ScreenshotTextExtractor textExtractor,
            ShiftScreenshotParser screenshotParser,
            ImportWarningRules warningRules,
            ScheduledShiftMatcher scheduledShiftMatcher,
            UserTimeService userTime,
            Clock clock,
            @Value("${flexbuddy.import.max-pixels:30000000}") long maxPixels
    ) {
        this.textExtractor = textExtractor;
        this.screenshotParser = screenshotParser;
        this.warningRules = warningRules;
        this.scheduledShiftMatcher = scheduledShiftMatcher;
        this.userTime = userTime;
        this.clock = clock;
        this.maxPixels = maxPixels;
    }

    public ShiftImportPreviewResponse createPreview(String email, MultipartFile screenshot) {

        if (screenshot == null || screenshot.isEmpty()) {
            throw new InvalidScreenshotException("A screenshot must be provided.");
        }

        String contentType = screenshot.getContentType();

        if (!"image/png".equals(contentType) && !"image/jpeg".equals(contentType)) {

            throw new InvalidScreenshotException("Unsupported screenshot type.");
        }

        OcrResult ocrResult;

        try (ImageInputStream input = ImageIO.createImageInputStream(screenshot.getInputStream())) {
            if (input == null) {
                throw new InvalidScreenshotException(
                        "The uploaded file is not a readable image."
                );
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);

            if (!readers.hasNext()) {
                throw new InvalidScreenshotException(
                        "The uploaded file is not a readable image."
                );
            }

            ImageReader reader = readers.next();

            try {
                reader.setInput(input);
                BufferedImage image = readWithinLimits(reader);
                ocrResult = textExtractor.extract(image);
            }
            finally {
                reader.dispose();
            }
        }
        catch (IOException exception) {
            throw new InvalidScreenshotException(
                    "The screenshot could not be read.",
                    exception
            );
        }

        ZoneId zone = email == null ? clock.getZone() : userTime.zone(email);
        LocalDateTime now = LocalDateTime.ofInstant(clock.instant(), zone);
        int year = now.getYear();
        ParseContext context = new ParseContext(now.toLocalDate(), null);
        ParsedShiftData parsedShift = screenshotParser.parse(ocrResult.lines(), year, context);
        ShiftStatus suggestedStatus = suggestStatus(parsedShift, now);
        parsedShift = warningRules.apply(parsedShift, ocrResult.meanConfidence(), context,
                suggestedStatus == ShiftStatus.SCHEDULED);
        List<DuplicateMatch> duplicates = suggestedStatus == ShiftStatus.COMPLETED
                ? scheduledShiftMatcher.match(email, parsedShift.date().value(), parsedShift.startTime().value(),
                        parsedShift.endTime().value(), parsedShift.station().value())
                : List.of();
        List<Integer> sourceLineRange = ocrResult.lines().isEmpty()
                ? List.of()
                : List.of(0, ocrResult.lines().size() - 1);
        ShiftCandidate candidate = new ShiftCandidate(
                0,
                parsedShift.station(),
                parsedShift.date(),
                parsedShift.startTime(),
                parsedShift.endTime(),
                parsedShift.basePay(),
                parsedShift.tips(),
                parsedShift.warnings(),
                duplicates,
                sourceLineRange,
                suggestedStatus
        );

        return new ShiftImportPreviewResponse(
                screenshot.getOriginalFilename(),
                contentType,
                screenshot.getSize(),
                "Found 1 shift. Review the imported values before saving.",
                ocrResult.text(),
                year,
                ocrResult.meanConfidence(),
                ocrResult.lines(),
                List.of(candidate)
        );
    }

    /** A block that starts after "now" in the driver's zone has not happened yet, so it is imported as scheduled. */
    static ShiftStatus suggestStatus(ParsedShiftData shift, LocalDateTime now) {
        LocalDate date = shift.date().value();
        if (date == null) return ShiftStatus.COMPLETED;
        LocalTime start = shift.startTime().value();
        boolean future = start == null ? date.isAfter(now.toLocalDate()) : LocalDateTime.of(date, start).isAfter(now);
        return future ? ShiftStatus.SCHEDULED : ShiftStatus.COMPLETED;
    }

    private BufferedImage readWithinLimits(ImageReader reader) throws IOException {
        int width = reader.getWidth(0);
        int height = reader.getHeight(0);

        if (width > MAX_SIDE || height > MAX_SIDE) {
            throw new InvalidScreenshotException(
                    "The screenshot is too large. Neither side may be over " + MAX_SIDE + " pixels."
            );
        }

        if ((long) width * height > maxPixels) {
            throw new InvalidScreenshotException(
                    "The screenshot is too large. Use an image under "
                            + Math.max(1, maxPixels / 1_000_000) + " megapixels."
            );
        }

        BufferedImage image = reader.read(0);

        if (image == null) {
            throw new InvalidScreenshotException(
                    "The uploaded file is not a readable image."
            );
        }

        return image;
    }
}
