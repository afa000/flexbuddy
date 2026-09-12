package com.angel.flexbuddy.service;

import java.awt.image.BufferedImage;
import java.awt.Rectangle;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.angel.flexbuddy.exception.ScreenshotOcrException;

import net.sourceforge.tess4j.Tesseract;
import net.sourceforge.tess4j.Word;
import net.sourceforge.tess4j.ITessAPI;

@Component
public class TesseractScreenshotTextExtractor implements ScreenshotTextExtractor {

    private final Path tessdataDirectory;
    private final ScreenshotPreprocessor preprocessor;

    public TesseractScreenshotTextExtractor(ScreenshotPreprocessor preprocessor) {
        this.preprocessor = preprocessor;
        tessdataDirectory = prepareTessdata();
    }

    @Override
    public OcrResult extract(BufferedImage image) {
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
