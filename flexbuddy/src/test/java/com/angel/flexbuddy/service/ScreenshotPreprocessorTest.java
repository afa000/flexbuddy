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
    void invertIfDark_turnsDarkBackgroundIntoLightBackground() {
        BufferedImage dark = solidImage(20, 20, new Color(20, 20, 20));
        BufferedImage grayscale = preprocessor.grayscale(dark);

        BufferedImage result = preprocessor.invertIfDark(grayscale);

        assertThat(result.getRaster().getSample(10, 10, 0)).isGreaterThan(230);
    }

    @Test
    void threshold_producesOnlyBlackAndWhitePixels() {
        BufferedImage image = new BufferedImage(3, 1, BufferedImage.TYPE_BYTE_GRAY);
        image.getRaster().setSample(0, 0, 0, 30);
        image.getRaster().setSample(1, 0, 0, 120);
        image.getRaster().setSample(2, 0, 0, 230);

        BufferedImage result = preprocessor.threshold(image);

        assertThat(result.getRaster().getSample(0, 0, 0)).isIn(0, 255);
        assertThat(result.getRaster().getSample(1, 0, 0)).isIn(0, 255);
        assertThat(result.getRaster().getSample(2, 0, 0)).isIn(0, 255);
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
