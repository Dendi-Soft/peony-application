package com.dendi.peony.service.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dendi.peony.exception.CorruptImageException;
import com.dendi.peony.exception.ImageTooLargeException;
import com.dendi.peony.exception.UnsupportedImageFormatException;
import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.GpsDirectory;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/**
 * Tests the personal-photo validation and processing pipeline (issue #44, ADR-008): JPEG
 * validation, EXIF-orientation normalisation, display and thumbnail scaling, and metadata
 * stripping. The processor is exercised directly; the HTTP and persistence behaviour lives in the
 * controller and migration tests.
 */
class UserPlantImageProcessorTest {

  private static final int MAX_UPLOAD_BYTES = 1_048_576;

  private final UserPlantImageProcessor processor = new UserPlantImageProcessor();

  /** A landscape photo larger than the display bound is downscaled to a 1280 px long edge. */
  @Test
  void testProcess_ScalesDisplayTo1280LongEdge_WhenImageIsLarger() throws Exception {
    UserPlantImageProcessor.ProcessedImage processed =
        processor.process(jpegOf(1600, 1200, Color.GREEN));

    BufferedImage display = decode(processed.display());
    assertEquals(1280, display.getWidth());
    assertEquals(960, display.getHeight());
    assertEquals(1280, processed.width());
    assertEquals(960, processed.height());
  }

  /** The thumbnail is always generated with a long edge of at most 256 px. */
  @Test
  void testProcess_Generates256LongEdgeThumbnail_WhenImageIsLarger() throws Exception {
    UserPlantImageProcessor.ProcessedImage processed =
        processor.process(jpegOf(1600, 1200, Color.GREEN));

    BufferedImage thumb = decode(processed.thumb());
    assertEquals(256, thumb.getWidth());
    assertEquals(192, thumb.getHeight());
  }

  /** A portrait photo is bounded by its long edge, not its width. */
  @Test
  void testProcess_ScalesPortraitByLongEdge_WhenHeightIsLarger() throws Exception {
    UserPlantImageProcessor.ProcessedImage processed =
        processor.process(jpegOf(900, 1800, Color.BLUE));

    BufferedImage display = decode(processed.display());
    assertEquals(640, display.getWidth());
    assertEquals(1280, display.getHeight());
    BufferedImage thumb = decode(processed.thumb());
    assertEquals(128, thumb.getWidth());
    assertEquals(256, thumb.getHeight());
  }

  /** An image already within the display bound keeps its dimensions. */
  @Test
  void testProcess_KeepsDimensions_WhenImageAlreadyWithinBounds() throws Exception {
    UserPlantImageProcessor.ProcessedImage processed =
        processor.process(jpegOf(800, 600, Color.RED));

    BufferedImage display = decode(processed.display());
    assertEquals(800, display.getWidth());
    assertEquals(600, display.getHeight());
    BufferedImage thumb = decode(processed.thumb());
    assertEquals(256, thumb.getWidth());
    assertEquals(192, thumb.getHeight());
  }

  /**
   * A photo carrying EXIF orientation 6 is rotated clockwise before scaling, so its stored
   * dimensions swap.
   */
  @Test
  void testProcess_RotatesImage_WhenExifOrientationIsSix() throws Exception {
    byte[] upload = withExifOrientation(jpegOf(400, 200, Color.ORANGE), 6);

    UserPlantImageProcessor.ProcessedImage processed = processor.process(upload);

    BufferedImage display = decode(processed.display());
    assertEquals(200, display.getWidth(), "orientation 6 must rotate the landscape photo");
    assertEquals(400, display.getHeight());
    assertEquals(200, processed.width());
    assertEquals(400, processed.height());
    BufferedImage thumb = decode(processed.thumb());
    assertEquals(128, thumb.getWidth());
    assertEquals(256, thumb.getHeight());
  }

  /** Re-encoding strips EXIF and GPS metadata from both stored sizes. */
  @Test
  void testProcess_StripsExifAndGps_WhenInputCarriesMetadata() throws Exception {
    byte[] upload = withExifOrientation(jpegOf(400, 200, Color.ORANGE), 3);

    UserPlantImageProcessor.ProcessedImage processed = processor.process(upload);

    assertLacksExifAndGps(processed.display());
    assertLacksExifAndGps(processed.thumb());
  }

  /** A non-JPEG signature is rejected as an unsupported format. */
  @Test
  void testProcess_RejectsNonJpeg_WhenPngSignature() {
    byte[] png = new byte[] {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    assertThrows(UnsupportedImageFormatException.class, () -> processor.process(png));
  }

  /** A JPEG signature with an undecodable body is corrupt data, not an unsupported format. */
  @Test
  void testProcess_RejectsCorruptJpeg_WhenSignatureValidButBodyUndecodable() {
    byte[] corrupt = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3, 4, 5};

    assertThrows(CorruptImageException.class, () -> processor.process(corrupt));
  }

  /** The upload cap matches the recognition endpoint's 1 MiB limit. */
  @Test
  void testProcess_RejectsUpload_WhenLargerThanOneMebibyte() {
    byte[] oversize = new byte[MAX_UPLOAD_BYTES + 1];
    oversize[0] = (byte) 0xFF;
    oversize[1] = (byte) 0xD8;
    oversize[2] = (byte) 0xFF;

    assertThrows(ImageTooLargeException.class, () -> processor.process(oversize));
  }

  /** An empty upload has no image to store. */
  @Test
  void testProcess_RejectsEmptyUpload_WhenNoBytes() {
    assertThrows(IllegalArgumentException.class, () -> processor.process(new byte[0]));
  }

  private static byte[] jpegOf(int width, int height, Color color) throws IOException {
    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    try {
      graphics.setColor(color);
      graphics.fillRect(0, 0, width, height);
    } finally {
      graphics.dispose();
    }
    return encode(image);
  }

  private static byte[] encode(BufferedImage image) throws IOException {
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      assertTrue(ImageIO.write(image, "jpeg", out), "a JPEG writer must be available");
      return out.toByteArray();
    }
  }

  private static BufferedImage decode(byte[] bytes) throws IOException {
    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
    assertTrue(image != null, "the processed bytes must be a decodable JPEG");
    return image;
  }

  /**
   * Inserts a little-endian EXIF APP1 segment carrying the given orientation plus a GPS IFD.
   *
   * <p>A JPEG has no writer-side EXIF support in ImageIO, so the fixture is assembled: SOI, APP1
   * with an "Exif" TIFF header, then the untouched baseline JPEG body.
   */
  private static byte[] withExifOrientation(byte[] jpeg, int orientation) throws Exception {
    int gpsIfdOffset = 38;
    byte[] tiff = new byte[56];
    int position = 0;
    // TIFF header: little-endian, magic 42, first IFD at offset 8.
    tiff[position++] = 'I';
    tiff[position++] = 'I';
    tiff[position++] = 42;
    tiff[position++] = 0;
    position = writeInt(tiff, position, 8);
    // IFD0 with two entries: Orientation and the GPS IFD pointer.
    tiff[position++] = 2;
    tiff[position++] = 0;
    position = writeExifEntry(tiff, position, 0x0112, 3, 1, orientation);
    position = writeExifEntry(tiff, position, 0x8825, 4, 1, gpsIfdOffset);
    position += 4; // next IFD = 0
    // GPS IFD with one ASCII entry so the GPS directory exists.
    tiff[position++] = 1;
    tiff[position++] = 0;
    tiff[position++] = 0x01; // GPSLatitudeRef
    tiff[position++] = 0x00;
    tiff[position++] = 2; // ASCII
    tiff[position++] = 0;
    position = writeInt(tiff, position, 2); // count
    tiff[position++] = 'N';
    tiff[position++] = 0;
    position += 2;
    position += 4; // next IFD = 0
    assertEquals(tiff.length, position, "the fixture must fill the TIFF block exactly");

    int segmentLength = 2 + 6 + tiff.length;
    byte[] exif = new byte[2 + segmentLength];
    exif[0] = (byte) 0xFF;
    exif[1] = (byte) 0xE1;
    exif[2] = (byte) (segmentLength >> 8);
    exif[3] = (byte) segmentLength;
    exif[4] = 'E';
    exif[5] = 'x';
    exif[6] = 'i';
    exif[7] = 'f';
    exif[8] = 0;
    exif[9] = 0;
    System.arraycopy(tiff, 0, exif, 10, tiff.length);
    assertEquals(exif.length, 2 + segmentLength);

    byte[] result = new byte[jpeg.length + exif.length];
    result[0] = jpeg[0];
    result[1] = jpeg[1];
    System.arraycopy(exif, 0, result, 2, exif.length);
    System.arraycopy(jpeg, 2, result, 2 + exif.length, jpeg.length - 2);

    // Precondition: the fixture must parse, otherwise the rotation assertions prove nothing.
    Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(result));
    ExifIFD0Directory exifDirectory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
    assertTrue(exifDirectory != null, "the fixture must carry an EXIF directory");
    assertEquals(orientation, exifDirectory.getInteger(ExifIFD0Directory.TAG_ORIENTATION));
    assertEquals(1, metadata.getDirectoriesOfType(GpsDirectory.class).size());
    return result;
  }

  private static int writeExifEntry(
      byte[] tiff, int position, int tag, int type, int count, int value) {
    tiff[position++] = (byte) tag;
    tiff[position++] = (byte) (tag >> 8);
    tiff[position++] = (byte) type;
    tiff[position++] = 0;
    position = writeInt(tiff, position, count);
    position = writeInt(tiff, position, value);
    return position;
  }

  private static int writeInt(byte[] target, int position, int value) {
    target[position++] = (byte) value;
    target[position++] = (byte) (value >> 8);
    target[position++] = (byte) (value >> 16);
    target[position++] = (byte) (value >> 24);
    return position;
  }

  private static void assertLacksExifAndGps(byte[] jpeg) throws Exception {
    Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(jpeg));
    assertTrue(
        metadata.getDirectoriesOfType(ExifIFD0Directory.class).isEmpty(),
        "the re-encoded image must carry no EXIF directory");
    assertTrue(
        metadata.getDirectoriesOfType(GpsDirectory.class).isEmpty(),
        "the re-encoded image must carry no GPS directory");
  }
}
