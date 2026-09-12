package com.angel.flexbuddy.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;

import org.springframework.stereotype.Component;

@Component
public class ScreenshotPreprocessor {

    private static final int TARGET_SHORT_SIDE = 1200;
    private static final double MAX_SCALE = 3.0;
    private static final int DARK_IMAGE_THRESHOLD = 110;
    private static final int PADDING = 20;

    public BufferedImage prepare(BufferedImage source) {
        BufferedImage scaled = upscale(source);
        BufferedImage grayscale = grayscale(scaled);
        BufferedImage normalized = invertIfDark(grayscale);
        BufferedImage thresholded = threshold(normalized);
        return pad(thresholded);
    }

    BufferedImage upscale(BufferedImage source) {
        int shortSide = Math.min(source.getWidth(), source.getHeight());
        double scale = shortSide >= TARGET_SHORT_SIDE
                ? 1.0
                : Math.min(MAX_SCALE, (double) TARGET_SHORT_SIDE / shortSide);
        if (scale == 1.0) return copy(source, source.getType() == 0 ? BufferedImage.TYPE_INT_RGB : source.getType());

        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(source, 0, 0, width, height, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    BufferedImage grayscale(BufferedImage source) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    BufferedImage invertIfDark(BufferedImage source) {
        long sum = 0;
        int count = source.getWidth() * source.getHeight();
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                sum += source.getRaster().getSample(x, y, 0);
            }
        }
        if (count == 0 || sum / count >= DARK_IMAGE_THRESHOLD) return source;

        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                result.getRaster().setSample(x, y, 0, 255 - source.getRaster().getSample(x, y, 0));
            }
        }
        return result;
    }

    BufferedImage threshold(BufferedImage source) {
        int[] histogram = new int[256];
        int total = source.getWidth() * source.getHeight();
        int extremePixels = 0;
        boolean hasDark = false;
        boolean hasLight = false;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int value = source.getRaster().getSample(x, y, 0);
                histogram[value]++;
                if (value <= 20 || value >= 235) extremePixels++;
                if (value <= 20) hasDark = true;
                if (value >= 235) hasLight = true;
            }
        }
        if (total > 0 && extremePixels >= total * .95 && hasDark && hasLight) return source;

        int cutoff = otsuThreshold(histogram, total);
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                int value = source.getRaster().getSample(x, y, 0) > cutoff ? 255 : 0;
                result.getRaster().setSample(x, y, 0, value);
            }
        }
        return result;
    }

    BufferedImage pad(BufferedImage source) {
        BufferedImage result = new BufferedImage(
                source.getWidth() + PADDING * 2,
                source.getHeight() + PADDING * 2,
                BufferedImage.TYPE_BYTE_GRAY
        );
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, result.getWidth(), result.getHeight());
            graphics.drawImage(source, PADDING, PADDING, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }

    private int otsuThreshold(int[] histogram, int total) {
        long weightedSum = 0;
        for (int i = 0; i < histogram.length; i++) weightedSum += (long) i * histogram[i];

        long backgroundSum = 0;
        int backgroundWeight = 0;
        double bestVariance = -1;
        int bestThreshold = 127;
        for (int i = 0; i < histogram.length; i++) {
            backgroundWeight += histogram[i];
            if (backgroundWeight == 0) continue;
            int foregroundWeight = total - backgroundWeight;
            if (foregroundWeight == 0) break;
            backgroundSum += (long) i * histogram[i];
            double backgroundMean = (double) backgroundSum / backgroundWeight;
            double foregroundMean = (double) (weightedSum - backgroundSum) / foregroundWeight;
            double variance = (double) backgroundWeight * foregroundWeight
                    * Math.pow(backgroundMean - foregroundMean, 2);
            if (variance > bestVariance) {
                bestVariance = variance;
                bestThreshold = i;
            }
        }
        return bestThreshold;
    }

    private BufferedImage copy(BufferedImage source, int type) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), type);
        Graphics2D graphics = result.createGraphics();
        try {
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        return result;
    }
}
