package com.dendi.peony.service.plant;

import com.dendi.peony.exception.CorruptImageException;
import com.dendi.peony.exception.ImageTooLargeException;
import com.dendi.peony.exception.UnsupportedImageFormatException;
import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Validates and normalises personal plant photos (issue #44, ADR-008).
 *
 * <p>Only JPEG uploads within the 1 MiB recognition cap are accepted. The decoded image has its
 * EXIF orientation applied, is downscaled so its long edge is at most 1280 px, and is re-encoded as
 * JPEG, which drops all EXIF and GPS metadata. A second, server-generated copy bounded at 256 px
 * serves the My Plants row.
 */
@Component
public class UserPlantImageProcessor {

  /** Upload cap shared with the recognition endpoint (1 MiB). */
  public static final int MAX_UPLOAD_BYTES = 1_048_576;

  /** Long edge of the stored display image. */
  static final int DISPLAY_LONG_EDGE = 1280;

  /** Long edge of the stored thumbnail. */
  static final int THUMB_LONG_EDGE = 256;

  private static final int JPEG_MAGIC_LENGTH = 3;

  /** EXIF orientation values run from 1 (normal) to 8 (flipped and rotated). */
  private static final int NORMAL_ORIENTATION = 1;

  /**
   * Processes one upload into the two stored sizes.
   *
   * @param upload the raw multipart bytes
   * @return the re-encoded display and thumbnail bytes plus the display dimensions
   * @throws IllegalArgumentException when the upload is empty
   * @throws ImageTooLargeException when the upload exceeds {@link #MAX_UPLOAD_BYTES}
   * @throws UnsupportedImageFormatException when the bytes are not JPEG
   * @throws CorruptImageException when the bytes carry a JPEG signature but cannot be decoded
   */
  public ProcessedImage process(byte[] upload) {
    if (upload == null || upload.length == 0) {
      throw new IllegalArgumentException("An image file is required");
    }
    if (upload.length > MAX_UPLOAD_BYTES) {
      throw new ImageTooLargeException("Image must be at most 1 MB");
    }
    if (!isJpeg(upload)) {
      throw new UnsupportedImageFormatException("Unsupported image format: only JPEG is accepted");
    }

    BufferedImage decoded = decode(upload);
    BufferedImage oriented = applyOrientation(decoded, readOrientation(upload));
    BufferedImage display = scaleToFit(oriented, DISPLAY_LONG_EDGE);
    BufferedImage thumb = scaleToFit(display, THUMB_LONG_EDGE);

    return new ProcessedImage(
        encodeJpeg(display), encodeJpeg(thumb), display.getWidth(), display.getHeight());
  }

  /** A processed upload: the display and thumbnail bytes and the display dimensions. */
  public record ProcessedImage(byte[] display, byte[] thumb, int width, int height) {}

  private static boolean isJpeg(byte[] bytes) {
    return bytes.length >= JPEG_MAGIC_LENGTH
        && bytes[0] == (byte) 0xFF
        && bytes[1] == (byte) 0xD8
        && bytes[2] == (byte) 0xFF;
  }

  private static BufferedImage decode(byte[] bytes) {
    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
      if (image == null) {
        throw new CorruptImageException("Image could not be decoded");
      }
      return image;
    } catch (IOException e) {
      throw new CorruptImageException("Image could not be decoded");
    }
  }

  /**
   * Reads the EXIF orientation tag; unreadable or absent metadata means the photo is upright.
   *
   * <p>The metadata is best-effort: only the pixel data is load-bearing, so a malformed EXIF block
   * must not reject an otherwise decodable photo.
   */
  private static int readOrientation(byte[] bytes) {
    try {
      Metadata metadata = ImageMetadataReader.readMetadata(new ByteArrayInputStream(bytes));
      ExifIFD0Directory directory = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
      if (directory == null) {
        return NORMAL_ORIENTATION;
      }
      Integer orientation = directory.getInteger(ExifIFD0Directory.TAG_ORIENTATION);
      return orientation == null ? NORMAL_ORIENTATION : orientation;
    } catch (ImageProcessingException | IOException e) {
      return NORMAL_ORIENTATION;
    }
  }

  /**
   * Applies the EXIF orientation so stored pixels are upright.
   *
   * <p>Orientations 5-8 transpose the image, so the target dimensions are swapped.
   */
  private static BufferedImage applyOrientation(BufferedImage source, int orientation) {
    if (orientation < 2 || orientation > 8) {
      return source;
    }
    int sourceWidth = source.getWidth();
    int sourceHeight = source.getHeight();
    boolean transposed = orientation >= 5;
    int targetWidth = transposed ? sourceHeight : sourceWidth;
    int targetHeight = transposed ? sourceWidth : sourceHeight;
    AffineTransform transform =
        switch (orientation) {
          case 2 -> new AffineTransform(-1, 0, 0, 1, sourceWidth, 0);
          case 3 -> new AffineTransform(-1, 0, 0, -1, sourceWidth, sourceHeight);
          case 4 -> new AffineTransform(1, 0, 0, -1, 0, sourceHeight);
          case 5 -> new AffineTransform(0, 1, 1, 0, 0, 0);
          case 6 -> new AffineTransform(0, 1, -1, 0, sourceHeight, 0);
          case 7 -> new AffineTransform(0, -1, -1, 0, sourceHeight, sourceWidth);
          case 8 -> new AffineTransform(0, -1, 1, 0, 0, sourceWidth);
          default -> throw new IllegalStateException("Unhandled EXIF orientation: " + orientation);
        };
    BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.drawImage(source, transform, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  /** Downscales so the long edge is at most {@code maxLongEdge}; smaller images are untouched. */
  private static BufferedImage scaleToFit(BufferedImage source, int maxLongEdge) {
    int sourceWidth = source.getWidth();
    int sourceHeight = source.getHeight();
    int longEdge = Math.max(sourceWidth, sourceHeight);
    if (longEdge <= maxLongEdge) {
      return source;
    }
    double factor = (double) maxLongEdge / longEdge;
    int targetWidth = Math.max(1, (int) Math.round(sourceWidth * factor));
    int targetHeight = Math.max(1, (int) Math.round(sourceHeight * factor));
    BufferedImage target = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.setRenderingHint(
          RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
      graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  /** Encodes a JPEG; the writer emits no EXIF or GPS blocks, so the upload's metadata is lost. */
  private static byte[] encodeJpeg(BufferedImage image) {
    BufferedImage rgb = toRgb(image);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      if (!ImageIO.write(rgb, "jpeg", out)) {
        throw new IllegalStateException("No JPEG writer is available");
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to encode the processed JPEG", e);
    }
  }

  /** Normalises to an alpha-free RGB image so the JPEG writer accepts every decoded type. */
  private static BufferedImage toRgb(BufferedImage image) {
    if (image.getType() == BufferedImage.TYPE_INT_RGB) {
      return image;
    }
    BufferedImage rgb =
        new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = rgb.createGraphics();
    try {
      graphics.drawImage(image, 0, 0, null);
    } finally {
      graphics.dispose();
    }
    return rgb;
  }
}
