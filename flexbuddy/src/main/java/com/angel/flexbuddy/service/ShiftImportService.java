package com.angel.flexbuddy.service;

import java.io.IOException;
import java.io.InputStream;
import java.awt.image.BufferedImage;
import java.time.Clock;
import java.time.LocalDate;
import java.time.Year;
import java.util.List;

import javax.imageio.ImageIO;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.angel.flexbuddy.dto.ShiftImportPreviewResponse;
import com.angel.flexbuddy.dto.ShiftCandidate;
import com.angel.flexbuddy.exception.InvalidScreenshotException;

@Service
public class ShiftImportService {

    private final ScreenshotTextExtractor textExtractor;
    private final ShiftScreenshotParser screenshotParser;
    private final ImportWarningRules warningRules;
    private final Clock clock;

    public ShiftImportService(
            ScreenshotTextExtractor textExtractor,
            ShiftScreenshotParser screenshotParser,
            ImportWarningRules warningRules,
            Clock clock
    ) {
        this.textExtractor = textExtractor;
        this.screenshotParser = screenshotParser;
        this.warningRules = warningRules;
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

        OcrResult ocrResult;

        try (InputStream inputStream = screenshot.getInputStream()) {
            BufferedImage image = ImageIO.read(inputStream);

            if (image == null) {
                throw new InvalidScreenshotException(
                        "The uploaded file is not a readable image."
                );
            }

            ocrResult = textExtractor.extract(image);
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
}
