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
      throw new UnsupportedImageFormatException("Unsupported image format");
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
    if (bytes == null || bytes.length < WEBP_MAGIC_LENGTH) {
      return false;
    }

    // Validate RIFF chunk signature: "RIFF" at offset 0 (byte-by-byte to avoid checked exceptions)
    if (bytes[0] != 'R' || bytes[1] != 'I' || bytes[2] != 'F' || bytes[3] != 'F') {
      return false;
    }

    // Validate file size field (bytes 4-7 must be a valid 4-byte unsigned integer)
    int fileSize = bytes[4] | (bytes[5] << 8) | (bytes[6] << 16) | (bytes[7] << 24);
    if (fileSize < WEBP_MAGIC_LENGTH || fileSize > Integer.MAX_VALUE - 8) {
      return false;
    }

    // Validate "WEBP" signature at offset 8 (byte-by-byte to avoid checked exceptions)
    if (bytes[8] != 'W' || bytes[9] != 'E' || bytes[10] != 'B' || bytes[11] != 'P') {
      return false;
    }

    return true;
  }
}
