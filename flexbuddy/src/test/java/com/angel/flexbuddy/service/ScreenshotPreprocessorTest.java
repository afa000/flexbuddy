package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;

import org.junit.jupiter.api.Test;

class ScreenshotPreprocessorTest {

    private final ScreenshotPreprocessor preprocessor = new ScreenshotPreprocessor();

    @Test
    void prepare_upscalesSmallImagesAndAddsWhitePadding() {
        BufferedImage source = solidImage(200, 400, Color.WHITE);

        BufferedImage result = preprocessor.prepare(source);

        assertThat(result.getWidth()).isEqualTo(640);
        assertThat(result.getHeight()).isEqualTo(1240);
        assertThat(result.getRaster().getSample(0, 0, 0)).isEqualTo(255);
    }

    @Test
    void scaledGrayscale_neverReturnsMoreThanFiveMegapixels() {
        // A long scrolling screenshot would otherwise be tripled to 1755 x 15000.
        BufferedImage strip = new BufferedImage(585, 5000, BufferedImage.TYPE_BYTE_GRAY);

        BufferedImage result = preprocessor.scaledGrayscale(strip);

        assertThat((long) result.getWidth() * result.getHeight()).isLessThanOrEqualTo(5_000_000L);
        assertThat(result.getHeight()).isGreaterThan(5000);
    }

    @Test
    void prepare_holdsAboutTwoGrayscaleCopiesOfTheScreenshot() {
        // Render gives the heap about 60 MB beside the app. Full-colour intermediate copies of a 5 megapixel scrolling
        // screenshot used up to 40 MB of it on top of the decoded image.
        BufferedImage strip = solidImage(1080, 4600, new Color(18, 18, 18));
        var threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();

        BufferedImage result = preprocessor.prepare(strip);

        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        long outputBytes = (long) result.getWidth() * result.getHeight();
        assertThat(allocated).isLessThan(outputBytes * 5 / 2);
    }

    @Test
    void prepare_doesNotCopyAnUnscaledPngInFullColour() {
        // ImageIO decodes a PNG screenshot with transparency as 4-byte ABGR; a 1440 x 3200 one is not upscaled.
        BufferedImage screenshot = new BufferedImage(1440, 3200, BufferedImage.TYPE_4BYTE_ABGR);
        var graphics = screenshot.createGraphics();
        graphics.setColor(Color.WHITE);
        graphics.fillRect(0, 0, screenshot.getWidth(), screenshot.getHeight());
        graphics.dispose();
        var threads = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        long before = threads.getCurrentThreadAllocatedBytes();

        BufferedImage result = preprocessor.prepare(screenshot);

        long allocated = threads.getCurrentThreadAllocatedBytes() - before;
        long outputBytes = (long) result.getWidth() * result.getHeight();
        assertThat(allocated).isLessThan(outputBytes * 5 / 2);
    }

    @Test
    void scaledGrayscale_keepsTheSizeWhenNoScalingIsNeeded() {
        BufferedImage source = solidImage(1300, 2600, Color.WHITE);

        BufferedImage result = preprocessor.scaledGrayscale(source);

        assertThat(result.getWidth()).isEqualTo(1300);
        assertThat(result.getHeight()).isEqualTo(2600);
        assertThat(result.getType()).isEqualTo(BufferedImage.TYPE_BYTE_GRAY);
    }

    @Test
    void invertIfDark_turnsDarkBackgroundIntoLightBackground() {
        BufferedImage dark = solidImage(20, 20, new Color(20, 20, 20));
        BufferedImage grayscale = preprocessor.grayscale(dark);

        preprocessor.invertIfDark(grayscale);

        assertThat(grayscale.getRaster().getSample(10, 10, 0)).isGreaterThan(230);
    }

    @Test
    void threshold_producesOnlyBlackAndWhitePixels() {
        BufferedImage image = new BufferedImage(3, 1, BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setSample(0, 0, 0, 30);
        image.getRaster().setSample(1, 0, 0, 120);
        image.getRaster().setSample(2, 0, 0, 230);

        preprocessor.threshold(image);

        assertThat(image.getRaster().getSample(0, 0, 0)).isIn(0, 255);
        assertThat(image.getRaster().getSample(1, 0, 0)).isIn(0, 255);
        assertThat(image.getRaster().getSample(2, 0, 0)).isIn(0, 255);
    }

    private BufferedImage solidImage(int width, int height, Color color) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        graphics.setColor(color);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }
}
