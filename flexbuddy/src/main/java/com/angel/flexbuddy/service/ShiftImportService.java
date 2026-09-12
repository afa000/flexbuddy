package com.angel.flexbuddy.service;

import java.io.IOException;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.Iterator;
import java.util.List;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.angel.flexbuddy.dto.ShiftImportPreviewResponse;
import com.angel.flexbuddy.dto.ShiftCandidate;
import com.angel.flexbuddy.exception.InvalidScreenshotException;

@Service
public class ShiftImportService {

    private static final int MAX_SIDE = 12_000;

    private final ScreenshotTextExtractor textExtractor;
    private final ShiftScreenshotParser screenshotParser;
    private final ImportWarningRules warningRules;
    private final Clock clock;
    private final long maxPixels;

    public ShiftImportService(
            ScreenshotTextExtractor textExtractor,
            ShiftScreenshotParser screenshotParser,
            ImportWarningRules warningRules,
            Clock clock,
            @Value("${flexbuddy.import.max-pixels:30000000}") long maxPixels
    ) {
        this.textExtractor = textExtractor;
        this.screenshotParser = screenshotParser;
        this.warningRules = warningRules;
        this.clock = clock;
        this.maxPixels = maxPixels;
    }

    public ShiftImportPreviewResponse createPreview(MultipartFile screenshot) {

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

        int year = Year.now(clock).getValue();
        ParseContext context = new ParseContext(LocalDate.now(clock), null);
        ParsedShiftData parsedShift = screenshotParser.parse(ocrResult.lines(), year, context);
        parsedShift = warningRules.apply(parsedShift, ocrResult.meanConfidence(), context);
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
                List.of(),
                sourceLineRange
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
