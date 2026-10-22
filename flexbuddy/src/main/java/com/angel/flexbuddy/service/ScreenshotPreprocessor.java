package com.angel.flexbuddy.service;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.WritableRaster;

import org.springframework.stereotype.Component;

@Component
public class ScreenshotPreprocessor {

    private static final int TARGET_SHORT_SIDE = 1200;
    private static final double MAX_SCALE = 3.0;
    private static final int DARK_IMAGE_THRESHOLD = 110;
    private static final int PADDING = 20;
    private static final int GRAYSCALE_STRIP_ROWS = 256;
    /** Upscaling never takes an image past this size, so a long scrolling screenshot stays bounded. */
    static final long MAX_OUTPUT_PIXELS = 5_000_000;

    /**
     * Only two images beside the caller's are ever allocated: the scaled grayscale copy, which is then inverted in
     * place, and the padded result. A full-colour scaled copy of a 5 megapixel screenshot was 20 MB on its own, and
     * Render leaves the heap about 60 MB beside the app. The image stays grayscale: Tesseract's Sauvola thresholding
     * turns it black and white area by area.
     */
    public BufferedImage prepare(BufferedImage source) {
        BufferedImage grayscale = scaledGrayscale(source);
        invertIfDark(grayscale);
        return pad(grayscale);
    }

    /** The source drawn straight into a new grayscale image, upscaled when it is small. */
    BufferedImage scaledGrayscale(BufferedImage source) {
        int shortSide = Math.min(source.getWidth(), source.getHeight());
        double scale = shortSide >= TARGET_SHORT_SIDE
                ? 1.0
                : Math.min(MAX_SCALE, (double) TARGET_SHORT_SIDE / shortSide);
        double limit = Math.sqrt((double) MAX_OUTPUT_PIXELS / ((long) source.getWidth() * source.getHeight()));
        boolean capped = limit < scale;
        scale = Math.min(scale, limit);
        if (scale <= 1.0) return grayscale(source);

        // Rounded sides could land a few pixels over the limit, so a capped image rounds down.
        int width = Math.max(1, (int) (capped ? Math.floor(source.getWidth() * scale) : Math.round(source.getWidth() * scale)));
        int height = Math.max(1, (int) (capped ? Math.floor(source.getHeight() * scale) : Math.round(source.getHeight() * scale)));
        BufferedImage result = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_GRAY);
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

    /**
     * Drawn in strips: the first unscaled draw of a decoded PNG made Java2D keep a converted full-colour copy of the
     * whole image, 18 MB for a 1440 x 3200 screenshot. A strip's copy is only strip-sized, and the pixels are identical.
     */
    BufferedImage grayscale(BufferedImage source) {
        BufferedImage result = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = result.createGraphics();
        try {
            for (int y = 0; y < source.getHeight(); y += GRAYSCALE_STRIP_ROWS) {
                int rows = Math.min(GRAYSCALE_STRIP_ROWS, source.getHeight() - y);
                graphics.drawImage(source.getSubimage(0, y, source.getWidth(), rows), 0, y, null);
            }
        } finally {
            graphics.dispose();
        }
        return result;
    }

    /** Inverts a mostly dark grayscale image in place, so text always ends up dark on light. */
    void invertIfDark(BufferedImage image) {
        WritableRaster raster = image.getRaster();
        long sum = 0;
        int count = image.getWidth() * image.getHeight();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                sum += raster.getSample(x, y, 0);
            }
        }
        if (count == 0 || sum / count >= DARK_IMAGE_THRESHOLD) return;

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                raster.setSample(x, y, 0, 255 - raster.getSample(x, y, 0));
            }
        }
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
}
