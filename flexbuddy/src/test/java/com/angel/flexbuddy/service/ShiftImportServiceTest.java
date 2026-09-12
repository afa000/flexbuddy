package com.angel.flexbuddy.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import com.angel.flexbuddy.dto.ShiftImportPreviewResponse;
import com.angel.flexbuddy.exception.InvalidScreenshotException;

@ExtendWith(MockitoExtension.class)
class ShiftImportServiceTest {

    @Mock
    private ScreenshotTextExtractor textExtractor;

    private ShiftImportService shiftImportService;

    @BeforeEach
    void setUp() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-09-07T12:00:00Z"),
                ZoneOffset.UTC
        );
        shiftImportService = new ShiftImportService(
                textExtractor,
                new ShiftScreenshotParser(),
                new ImportWarningRules(),
                fixedClock,
                30_000_000L
        );
    }

    @Test
    void createPreview_returnsMetadataForReadablePng() throws IOException {
        byte[] imageBytes = createPngBytes();
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshot",
                "shift.png",
                "image/png",
                imageBytes
        );
        when(textExtractor.extract(any(BufferedImage.class)))
                .thenReturn(OcrResult.fromLines(java.util.List.of(
                        line("Windsor (DCY1) - Amazon.com", 92, 0),
                        line("Sunday, 9/6", 90, 1),
                        line("04:00 - 07:30 (3 hr 30 min)", 88, 2),
                        line("$124.50", 86, 3)
                )));

        ShiftImportPreviewResponse result = shiftImportService.createPreview(screenshot);

        assertThat(result.originalFilename()).isEqualTo("shift.png");
        assertThat(result.contentType()).isEqualTo("image/png");
        assertThat(result.size()).isEqualTo(imageBytes.length);
        assertThat(result.message()).contains("Found 1 shift");
        assertThat(result.rawText()).contains("Windsor (DCY1)");
        assertThat(result.year()).isEqualTo(2026);
        assertThat(result.meanConfidence()).isBetween(86, 92);
        assertThat(result.shifts()).hasSize(1);
        var shift = result.shifts().getFirst();
        assertThat(shift.station().value()).isEqualTo("DCY1");
        assertThat(shift.date().value()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(shift.startTime().value()).isEqualTo(LocalTime.of(4, 0));
        assertThat(shift.endTime().value()).isEqualTo(LocalTime.of(7, 30));
        assertThat(shift.basePay().value()).isEqualByComparingTo(new BigDecimal("124.50"));
        assertThat(shift.tips().value()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(shift.warnings()).isEmpty();
    }

    @Test
    void createPreview_throwsForEmptyScreenshot() {
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshot",
                "shift.png",
                "image/png",
                new byte[0]
        );

        assertThatThrownBy(() -> shiftImportService.createPreview(screenshot))
                .isInstanceOf(InvalidScreenshotException.class)
                .hasMessage("A screenshot must be provided.");
    }

    @Test
    void createPreview_throwsForUnsupportedContentType() {
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshot",
                "notes.txt",
                "text/plain",
                "not an image".getBytes()
        );

        assertThatThrownBy(() -> shiftImportService.createPreview(screenshot))
                .isInstanceOf(InvalidScreenshotException.class)
                .hasMessage("Unsupported screenshot type.");
    }

    @Test
    void createPreview_throwsWhenImageCannotBeDecoded() {
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshot",
                "fake.png",
                "image/png",
                "not really a PNG".getBytes()
        );

        assertThatThrownBy(() -> shiftImportService.createPreview(screenshot))
                .isInstanceOf(InvalidScreenshotException.class)
                .hasMessage("The uploaded file is not a readable image.");
    }

    @Test
    void createPreview_rejectsAnImageWithMorePixelsThanTheConfiguredLimit() throws IOException {
        ShiftImportService limited = new ShiftImportService(textExtractor, new ShiftScreenshotParser(),
                new ImportWarningRules(), Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC), 10_000L);
        MockMultipartFile screenshot = new MockMultipartFile("screenshot", "huge.png", "image/png",
                createPngBytes(200, 200));

        assertThatThrownBy(() -> limited.createPreview(screenshot))
                .isInstanceOf(InvalidScreenshotException.class)
                .hasMessageContaining("too large");
    }

    @Test
    void createPreview_rejectsAnExtremeAspectRatioOnTheSideCap() throws IOException {
        ShiftImportService service = new ShiftImportService(textExtractor, new ShiftScreenshotParser(),
                new ImportWarningRules(), Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC),
                30_000_000L);
        MockMultipartFile screenshot = new MockMultipartFile("screenshot", "wide.png", "image/png",
                createPngBytes(12_001, 2));

        assertThatThrownBy(() -> service.createPreview(screenshot))
                .isInstanceOf(InvalidScreenshotException.class)
                .hasMessageContaining("Neither side may be over 12000 pixels");
    }

    @Test
    void createPreview_stillReadsAnImageInsideTheConfiguredLimit() throws IOException {
        ShiftImportService limited = new ShiftImportService(textExtractor, new ShiftScreenshotParser(),
                new ImportWarningRules(), Clock.fixed(Instant.parse("2026-09-07T12:00:00Z"), ZoneOffset.UTC), 10_000L);
        when(textExtractor.extract(any(BufferedImage.class))).thenReturn(OcrResult.fromLines(java.util.List.of(
                line("Windsor (DCY1) - Amazon.com", 92, 0))));
        MockMultipartFile screenshot = new MockMultipartFile("screenshot", "small.png", "image/png",
                createPngBytes(50, 50));

        assertThat(limited.createPreview(screenshot).shifts()).hasSize(1);
    }

    private byte[] createPngBytes(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private byte[] createPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }

    private OcrLine line(String text, int confidence, int index) {
        return new OcrLine(text, confidence, 0, index * 20, 200, 18, index);
    }
}
