package com.dendi.peony.service.plantrecognition;

import com.dendi.peony.exception.UnsupportedImageFormatException;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import javax.imageio.ImageIO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Converts WebP images to JPEG for PlantNet API compatibility.
 *
 * <p>PlantNet's identify endpoint accepts JPEG and PNG uploads. WebP uploads are decoded with the
 * TwelveMonkeys ImageIO WebP reader and re-encoded as JPEG.
 */
@Component
public class WebpConverter {

  /** Minimum length of the RIFF/WEBP magic header ("RIFF" + 4 size bytes + "WEBP"). */
  private static final int WEBP_MAGIC_LENGTH = 12;

  private static final Logger log = LoggerFactory.getLogger(WebpConverter.class);

  /**
   * Converts WebP image bytes to JPEG.
   *
   * @param webpBytes WebP image bytes (RIFF/WEBP container)
   * @return JPEG-encoded image bytes
   * @throws UnsupportedImageFormatException if the bytes are not a decodable WebP image
   */
  public byte[] convertWebpToJpeg(byte[] webpBytes) {
    if (!isWebp(webpBytes)) {
      throw new UnsupportedImageFormatException(
          "Image bytes are not a WebP image. Supported uploads: JPEG, PNG, WebP.");
    }

    BufferedImage image;
    try {
      image = ImageIO.read(new ByteArrayInputStream(webpBytes));
    } catch (IOException e) {
      throw new UnsupportedImageFormatException("Failed to decode WebP image", e);
    }
    if (image == null) {
      throw new UnsupportedImageFormatException("No WebP reader could decode the image");
    }

    try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      if (!ImageIO.write(image, "jpeg", out)) {
        throw new UnsupportedImageFormatException("No JPEG writer available for encoding");
      }
      log.debug("Converted {} WebP bytes to {} JPEG bytes", webpBytes.length, out.size());
      return out.toByteArray();
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to encode JPEG image", e);
    }
  }

  private static boolean isWebp(byte[] bytes) {
    // RIFF....WEBP magic: "RIFF" at offset 0, "WEBP" at offset 8
    return bytes != null
        && bytes.length >= WEBP_MAGIC_LENGTH
        && bytes[0] == 'R'
        && bytes[1] == 'I'
        && bytes[2] == 'F'
        && bytes[3] == 'F'
        && bytes[8] == 'W'
        && bytes[9] == 'E'
        && bytes[10] == 'B'
        && bytes[11] == 'P';
  }
}
