package com.angel.flexbuddy.service;

import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import com.angel.flexbuddy.exception.ScreenshotBusyException;
import com.angel.flexbuddy.exception.ScreenshotOcrException;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import net.sourceforge.tess4j.ITessAPI;

@Component
public class TesseractScreenshotTextExtractor implements ScreenshotTextExtractor {

    private final Path tessdataDirectory;
    private final ScreenshotPreprocessor preprocessor;
    private final Semaphore ocrPermit = new Semaphore(1, true);
    private final Duration ocrWait;

    public TesseractScreenshotTextExtractor(
            ScreenshotPreprocessor preprocessor,
            @Value("${flexbuddy.import.ocr-wait:20s}") Duration ocrWait
    ) {
        this.preprocessor = preprocessor;
        this.ocrWait = ocrWait;
        tessdataDirectory = prepareTessdata();
    }

    /** Text extraction runs one at a time, so two imports never hold their images and Tesseract's memory together. */
    @Override
    public OcrResult extract(BufferedImage image) {
        try {
            if (!ocrPermit.tryAcquire(ocrWait.toMillis(), TimeUnit.MILLISECONDS)) {
                throw new ScreenshotBusyException("error.import.busy");
            }
        }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ScreenshotBusyException("error.import.busy");
        }
        try {
            return read(image);
        }
        finally {
            ocrPermit.release();
        }
    }

    Semaphore ocrPermit() {
        return ocrPermit;
    }

    private OcrResult read(BufferedImage image) {
        BufferedImage preparedImage = preprocessor.prepare(image);
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataDirectory.toString());
        tesseract.setLanguage("eng");
        tesseract.setPageSegMode(preparedImage.getHeight() > preparedImage.getWidth() * 1.6 ? 4 : 6);
        tesseract.setVariable("user_defined_dpi", "300");
        tesseract.setVariable("preserve_interword_spaces", "1");

        try {
            List<Word> words = tesseract.getWords(
                    preparedImage,
                    ITessAPI.TessPageIteratorLevel.RIL_TEXTLINE
            );
            List<OcrLine> lines = new ArrayList<>();
            for (Word word : words) {
                if (word.getText() == null || word.getText().isBlank()) continue;
                Rectangle bounds = word.getBoundingBox();
                lines.add(new OcrLine(
                        word.getText(),
                        Math.round(word.getConfidence()),
                        bounds.x,
                        bounds.y,
                        bounds.width,
                        bounds.height,
                        lines.size()
                ));
            }
            return OcrResult.fromLines(lines);
        }
        catch (RuntimeException exception) {
            throw new ScreenshotOcrException(
                    exception,
                    "error.import.textNotExtracted"
            );
        }
    }

    private Path prepareTessdata() {
        try (InputStream trainedData = getClass()
                .getResourceAsStream("/tessdata/eng.traineddata")) {

            if (trainedData == null) {
                throw new IllegalStateException(
                        "The English Tesseract training data is missing."
                );
            }

            Path directory = Files.createTempDirectory("flexbuddy-tessdata-");
            Path destination = directory.resolve("eng.traineddata");
            Files.copy(trainedData, destination, StandardCopyOption.REPLACE_EXISTING);

            destination.toFile().deleteOnExit();
            directory.toFile().deleteOnExit();

            return directory;
        }
        catch (IOException exception) {
            throw new IllegalStateException(
                    "The Tesseract training data could not be prepared.",
                    exception
            );
        }
    }
}
