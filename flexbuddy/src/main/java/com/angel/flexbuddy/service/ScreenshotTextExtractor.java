package com.angel.flexbuddy.service;

import java.awt.image.BufferedImage;

public interface ScreenshotTextExtractor {

    OcrResult extract(BufferedImage image);
}
