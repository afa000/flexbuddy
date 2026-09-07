package com.angel.flexbuddy.service;

import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.Year;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.angel.flexbuddy.dto.ShiftImportPreviewResponse;
import com.angel.flexbuddy.exception.InvalidScreenshotException;

@Service
public class ShiftImportService {

    private final ScreenshotTextExtractor textExtractor;
    private final ShiftScreenshotParser screenshotParser;
    private final Clock clock;

    public ShiftImportService(
            ScreenshotTextExtractor textExtractor,
            ShiftScreenshotParser screenshotParser,
            Clock clock
    ) {
        this.textExtractor = textExtractor;
        this.screenshotParser = screenshotParser;
        this.clock = clock;
    }

    public ShiftImportPreviewResponse createPreview(MultipartFile screenshot) {

        if (screenshot == null || screenshot.isEmpty()) {
            throw new InvalidScreenshotException("A screenshot must be provided.");
        }

        String contentType = screenshot.getContentType();

        if (!"image/png".equals(contentType) && !"image/jpeg".equals(contentType)) {

            throw new InvalidScreenshotException("Unsupported screenshot type.");
        }

        String rawText;

        try (InputStream inputStream = screenshot.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);

            if (image == null) {
                throw new InvalidScreenshotException(
                        "The uploaded file is not a readable image."
                );
            }

            rawText = textExtractor.extract(image);
        }
        catch (IOException exception) {
            throw new InvalidScreenshotException(
                    "The screenshot could not be read.",
                    exception
            );
        }

        int year = Year.now(clock).getValue();
        ParsedShiftData parsedShift = screenshotParser.parse(rawText, year);

        return new ShiftImportPreviewResponse(
                screenshot.getOriginalFilename(),
                contentType,
                screenshot.getSize(),
                "Screenshot processed successfully.",
                rawText,
                year,
                parsedShift.station(),
                parsedShift.date(),
                parsedShift.startTime(),
                parsedShift.endTime(),
                parsedShift.basePay(),
                parsedShift.tips(),
                parsedShift.warnings()
        );
    }
}
