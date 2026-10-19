package com.dendi.peony.service.plantrecognition;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dendi.peony.TestFixtures;
import com.dendi.peony.exception.UnsupportedImageFormatException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Tests for WebP to JPEG conversion at the WebpConverter seam. */
class WebpConverterTest {

  private final WebpConverter converter = new WebpConverter();

  /** Converts a 16x16 WebP fixture to JPEG and verifies the result is a decodable JPEG image. */
  @Test
  void testConvertWebpToJpeg_ReturnsJpegBytes_WhenInputIsValidWebp() throws IOException {
    byte[] webp = TestFixtures.read("fixtures/webp-fixture.webp");

    byte[] jpeg = converter.convertWebpToJpeg(webp);

    // JPEG magic number is an independent source of truth (FF D8 FF)
    assertTrue(jpeg.length > 2, "Expected non-trivial JPEG output");
    assertEquals((byte) 0xFF, jpeg[0]);
    assertEquals((byte) 0xD8, jpeg[1]);
    assertEquals((byte) 0xFF, jpeg[2]);

    BufferedImage image = ImageIO.read(new ByteArrayInputStream(jpeg));
    assertNotNull(image, "Output must be decodable as an image");
    assertEquals(16, image.getWidth());
    assertEquals(16, image.getHeight());
  }

  /** Rejects non-WebP bytes so callers never mislabel the resulting media type. */
  @Test
  void testConvertWebpToJpeg_ThrowsUnsupportedImageFormatException_WhenInputIsNotWebp() {
    byte[] jpeg = generatedJpeg();

    assertThrows(UnsupportedImageFormatException.class, () -> converter.convertWebpToJpeg(jpeg));
  }

  /** Rejects WebP container bytes that no decoder can read. */
  @Test
  void testConvertWebpToJpeg_ThrowsUnsupportedImageFormatException_WhenWebpBytesAreCorrupt() {
    byte[] corrupt =
        new byte[] {
          'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 'g', 'a', 'r', 'b', 'a', 'g', 'e'
        };

    assertThrows(UnsupportedImageFormatException.class, () -> converter.convertWebpToJpeg(corrupt));
  }

  private byte[] generatedJpeg() {
    BufferedImage image = new BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ImageIO.write(image, "jpeg", out);
      return out.toByteArray();
    } catch (IOException e) {
      throw new IllegalStateException("Failed to generate JPEG test image", e);
    }
  }
}
