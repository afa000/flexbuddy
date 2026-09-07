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
                fixedClock
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
                .thenReturn("""
                        Windsor (DCY1) - Amazon.com
                        Sunday, 9/6
                        04:00 - 07:30 (3 hr 30 min)
                        $124.50
                        """);

        ShiftImportPreviewResponse result = shiftImportService.createPreview(screenshot);

        assertThat(result.getOriginalFilename()).isEqualTo("shift.png");
        assertThat(result.getContentType()).isEqualTo("image/png");
        assertThat(result.getSize()).isEqualTo(imageBytes.length);
        assertThat(result.getMessage()).isEqualTo("Screenshot processed successfully.");
        assertThat(result.getRawText()).contains("Windsor (DCY1)");
        assertThat(result.getYear()).isEqualTo(2026);
        assertThat(result.getStation()).isEqualTo("DCY1");
        assertThat(result.getDate()).isEqualTo(LocalDate.of(2026, 9, 6));
        assertThat(result.getStartTime()).isEqualTo(LocalTime.of(4, 0));
        assertThat(result.getEndTime()).isEqualTo(LocalTime.of(7, 30));
        assertThat(result.getBasePay()).isEqualByComparingTo(new BigDecimal("124.50"));
        assertThat(result.getTips()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.getWarnings()).isEmpty();
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

    private byte[] createPngBytes() throws IOException {
        BufferedImage image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "png", output);
        return output.toByteArray();
    }
}
