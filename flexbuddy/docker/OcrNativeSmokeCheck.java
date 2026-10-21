import java.awt.Color;
import java.awt.image.BufferedImage;
import com.angel.flexbuddy.service.ScreenshotPreprocessor;
import com.angel.flexbuddy.service.TesseractScreenshotTextExtractor;

/** Run inside the Linux image: mocked unit tests cannot detect native linking failures. */
public final class OcrNativeSmokeCheck {
    public static void main(String[] args) {
        BufferedImage image = new BufferedImage(1200, 1600, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            graphics.dispose();
        }
        var extractor = new TesseractScreenshotTextExtractor(new ScreenshotPreprocessor());
        extractor.extract(image);
        System.out.println("Native OCR initialization and image processing passed.");
    }
}
