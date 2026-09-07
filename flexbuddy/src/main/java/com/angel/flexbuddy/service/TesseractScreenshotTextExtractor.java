package com.angel.flexbuddy.service;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Component;

import com.angel.flexbuddy.exception.ScreenshotOcrException;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.TesseractException;

@Component
public class TesseractScreenshotTextExtractor implements ScreenshotTextExtractor {

    private final Path tessdataDirectory;

    public TesseractScreenshotTextExtractor() {
        tessdataDirectory = prepareTessdata();
    }

    @Override
    public String extract(BufferedImage image) {
        Tesseract tesseract = new Tesseract();
        tesseract.setDatapath(tessdataDirectory.toString());
        tesseract.setLanguage("eng");

        try {
            return tesseract.doOCR(image).trim();
        }
        catch (TesseractException exception) {
            throw new ScreenshotOcrException(
                    "Text could not be extracted from the screenshot.",
                    exception
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
