package com.dendi.peony.service.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dendi.peony.exception.CorruptImageException;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Tests the PlantNet species-image processor (issue #45, ADR-008): the stored fallback is a
 * decodable JPEG whose long edge is 256 px, while smaller downloads are left at their size.
 */
class SpeciesImageProcessorTest {

  private final SpeciesImageProcessor processor = new SpeciesImageProcessor();

  /** A landscape download is downscaled to a 256 px long edge, keeping its aspect ratio. */
  @Test
  void testProcess_DownscalesLandscape_WhenLongEdgeOverLimit() throws Exception {
    BufferedImage processed = decode(processor.process(jpegOf(1280, 960)));

    assertEquals(256, processed.getWidth());
    assertEquals(192, processed.getHeight());
  }

  /** A portrait download is bounded on its long edge. */
  @Test
  void testProcess_DownscalesPortrait_WhenLongEdgeOverLimit() throws Exception {
    BufferedImage processed = decode(processor.process(jpegOf(900, 1800)));

    assertEquals(128, processed.getWidth());
    assertEquals(256, processed.getHeight());
  }

  /** An already-small download keeps its dimensions. */
  @Test
  void testProcess_KeepsSmallImage_WhenLongEdgeWithinLimit() throws Exception {
    BufferedImage processed = decode(processor.process(jpegOf(100, 80)));

    assertEquals(100, processed.getWidth());
    assertEquals(80, processed.getHeight());
  }

  /** The output is always a decodable JPEG. */
  @Test
  void testProcess_EncodesJpeg_WhenInputDecodable() throws Exception {
    byte[] processed = processor.process(jpegOf(640, 480));

    assertTrue(processed.length > 0);
    BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(processed));
    assertTrue(decoded != null, "the stored fallback must be a decodable image");
  }

  /** The long-edge overload bounds the image at the requested edge (512 px for the catalogue). */
  @Test
  void testProcessWithLongEdge_DownscalesToRequestedEdge_WhenLongEdgeOverLimit() throws Exception {
    BufferedImage processed = decode(processor.process(jpegOf(1280, 960), 512));

    assertEquals(512, processed.getWidth());
    assertEquals(384, processed.getHeight());
  }

  /** The long-edge overload leaves an image smaller than the requested edge untouched. */
  @Test
  void testProcessWithLongEdge_KeepsSmallImage_WhenLongEdgeWithinLimit() throws Exception {
    BufferedImage processed = decode(processor.process(jpegOf(400, 300), 512));

    assertEquals(400, processed.getWidth());
    assertEquals(300, processed.getHeight());
  }

  /** Undecodable bytes are rejected rather than stored. */
  @Test
  void testProcess_RejectsUndecodableBytes_WhenNotAnImage() {
    assertThrows(CorruptImageException.class, () -> processor.process(new byte[] {1, 2, 3, 4}));
  }

  /** Empty bytes carry no image. */
  @Test
  void testProcess_RejectsEmptyDownload_WhenNoBytes() {
    assertThrows(CorruptImageException.class, () -> processor.process(new byte[0]));
  }

  private static byte[] jpegOf(int width, int height) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(Color.GREEN);
      graphics.fillRect(0, 0, width, height);
    } finally {
      graphics.dispose();
    }
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      ImageIO.write(image, "jpeg", out);
      return out.toByteArray();
    }
  }

  private static BufferedImage decode(byte[] bytes) throws IOException {
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
    assertTrue(image != null, "the processed bytes must be a decodable JPEG");
    return image;
  }
}
