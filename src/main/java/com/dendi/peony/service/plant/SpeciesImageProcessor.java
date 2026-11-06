package com.dendi.peony.service.plant;

import com.dendi.peony.exception.CorruptImageException;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Component;

/**
 * Turns a downloaded PlantNet image into the stored fallback copy (issue #45, ADR-008).
 *
 * <p>The fallback is a single 256 px JPEG: the ticket stores only that file at position 0, so it
 * serves both the thumbnail and the gallery hero. The 1 MiB personal-upload cap does not apply
 * here; the download client already enforces its own 5 MiB wire cap. The re-encode drops metadata,
 * including any EXIF the source image carried.
 */
@Component
public class SpeciesImageProcessor {

  /** Long edge of the stored fallback image. */
  public static final int LONG_EDGE = 256;

  /**
   * Validates and downscales one downloaded image to the PlantNet fallback size.
   *
   * @param download bytes returned by the image download client
   * @return a JPEG whose long edge is at most {@link #LONG_EDGE} px
   * @throws CorruptImageException when the bytes cannot be decoded as an image
   */
  public byte[] process(byte[] download) {
    return process(download, LONG_EDGE);
  }

  /**
   * Validates and downscales one downloaded image to a requested long edge.
   *
   * <p>The curated catalogue images use 512 px, while the PlantNet fallback keeps 256 px.
   *
   * @param download bytes returned by the image download client
   * @param longEdge maximum length of the image's longer side, in pixels
   * @return a JPEG whose long edge is at most {@code longEdge} px
   * @throws CorruptImageException when the bytes cannot be decoded as an image
   * @throws IllegalArgumentException when {@code longEdge} is not positive
   */
  public byte[] process(byte[] download, int longEdge) {
    if (longEdge <= 0) {
      throw new IllegalArgumentException("The image long edge must be positive: " + longEdge);
    }
    if (download == null || download.length == 0) {
      throw new CorruptImageException("Image could not be decoded");
    }
    BufferedImage decoded = decode(download);
    return encodeJpeg(scaleToFit(decoded, longEdge));
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

  /** Encodes a JPEG; the writer emits no EXIF or GPS blocks. */
  private static byte[] encodeJpeg(BufferedImage image) {
    BufferedImage rgb = toRgb(image);
    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      if (!ImageIO.write(rgb, "jpeg", out)) {
        throw new IllegalStateException("No JPEG writer is available");
      }
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to encode the species-image JPEG", e);
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
