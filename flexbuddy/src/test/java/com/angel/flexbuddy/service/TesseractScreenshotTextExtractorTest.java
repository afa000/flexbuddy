package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.angel.flexbuddy.exception.ScreenshotBusyException;

class TesseractScreenshotTextExtractorTest {

    @Test
    void aSecondReadWaitsThenIsToldToTryAgain() throws InterruptedException {
        TesseractScreenshotTextExtractor extractor = new TesseractScreenshotTextExtractor(
                new ScreenshotPreprocessor(), Duration.ofMillis(100));
        extractor.ocrPermit().acquire();
        try {
            assertThatThrownBy(() -> extractor.extract(new BufferedImage(10, 10, BufferedImage.TYPE_INT_RGB)))
                    .isInstanceOf(ScreenshotBusyException.class)
                    .hasMessage("Another screenshot is being read. Try again in a moment.");
        } finally {
            extractor.ocrPermit().release();
        }
        assertThat(extractor.ocrPermit().availablePermits()).isEqualTo(1);
    }
}
