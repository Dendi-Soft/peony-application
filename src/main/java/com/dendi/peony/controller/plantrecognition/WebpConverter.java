package com.dendi.peony.controller.plantrecognition;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** Utility class to convert WebP images to JPEG format for PlantNet API compatibility. */
@Slf4j
@Component
public class WebpConverter {

  /**
   * Converts a byte array of image (JPEG/PNG/WebP) to the target format if needed.
   *
   * @param imageData Raw image bytes
   * @param sourceFormat Source MIME type (e.g., "image/webp", "image/jpeg")
   * @param targetFormat Target MIME type ("image/jpeg" or "image/png")
   * @return Converted image bytes in the target format, or original if no conversion needed
   */
  public byte[] convertImage(byte[] imageData, String sourceFormat, String targetFormat) {
    // If already JPEG (PlantNet's preferred and most reliable format), return as-is
    if ("image/jpeg".equalsIgnoreCase(targetFormat)) {
      if ("image/jpeg".equals(sourceFormat.toUpperCase())
          || "webp".equalsIgnoreCase(sourceFormat.toLowerCase())) {
        log.debug("Source is {} - will be handled appropriately", sourceFormat);
        // For WebP, we'll convert it. For JPEG, return directly.
        if (sourceFormat.equalsIgnoreCase("image/jpeg")) {
          return imageData;
        } else {
          // It's WebP or another format that needs conversion to JPEG
          try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
            if (image != null) {
              log.debug("Successfully loaded {} as BufferedImage", sourceFormat);

              String detectedFormat = getImageFormatFromBufferedImage(image);
              if ("jpeg".equalsIgnoreCase(detectedFormat)) {
                return imageData; // Already JPEG from ImageIO's perspective
              }
            }
          } catch (Exception e) {
            log.warn("Error reading image: {}", e.getMessage());
          }
        }
      } else {
        // Source is PNG or other format, convert to target if needed
      }
    }

    try {
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));

      if (image == null) {
        log.warn("ImageIO could not read bytes directly - trying WebP detection");
        // If we can't detect the format via ImageIO, check magic bytes for WebP
        return attemptWebpToJpegConversion(imageData);
      }

      String detectedFormat = getImageFormatFromBufferedImage(image);

      if ("jpeg".equalsIgnoreCase(detectedFormat)) {
        log.debug("Detected image is JPEG - no conversion needed");
        return imageData;
      } else if ("png".equalsIgnoreCase(detectedFormat)) {
        // PNG can be sent directly to PlantNet
        log.debug("Detected image is PNG - returning as-is");
        return imageData;
      }

    } catch (Exception e) {
      log.error("Error reading image: {}", e.getMessage(), e);
    }

    // Try WebP->JPEG conversion as fallback for any non-JPEG formats that need it
    byte[] result = attemptWebpToJpegConversion(imageData);
    if (result != null && result.length > 0) {
      return result;
    }

    // Return original - might be valid PNG which PlantNet accepts directly
    log.debug("Returning original image bytes");
    return imageData;
  }

  /** Tries to convert WebP/other format bytes to JPEG. */
  private byte[] attemptWebpToJpegConversion(byte[] webpData) {
    try {
      BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(webpData));

      if (img == null) {
        log.warn("ImageIO failed to load image from bytes - returning original data");
        return webpData; // Return original - let caller decide what to do with it
      }

      String detectedFormat = getImageFormatFromBufferedImage(img);
      if ("jpeg".equalsIgnoreCase(detectedFormat)) {
        return webpData; // Already JPEG
      }

      log.debug("Converting from {} to JPEG", detectedFormat != null ? detectedFormat : "unknown");

      java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
      ImageIO.write(img, "jpeg", out); // Using default JPEG quality

      return out.toByteArray();

    } catch (Exception e) {
      log.warn("Conversion failed - returning original data: {}", webpData.length, e);
      return null;
    }
  }

  /** Gets detected format from BufferedImage. */
  private String getImageFormatFromBufferedImage(BufferedImage img) {
    if (img == null) {
      return "";
    }

    // Try both JPEG and PNG to determine what we have based on write success and file size
    try (java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream()) {
      ImageIO.write(img, "jpeg", baos);

      if (baos.size() >= 10) {
        return "jpeg";
      }

    } catch (Exception e) {
      // JPEG write failed, try PNG instead
    }

    try (java.io.ByteArrayOutputStream pngBaos = new java.io.ByteArrayOutputStream()) {
      ImageIO.write(img, "png", pngBaos);

      if (pngBaos.size() >= 10) {
        return "png";
      }

    } catch (Exception e) {
      log.warn("Both JPEG and PNG write failed: {}", e.getMessage());
    }

    // Fallback: try to determine from BufferedImage properties or class name hints
    String hint = img.getClass().getSimpleName();
    if (hint.contains("RGB") || hint.contains("ARGB")) {
      return "jpeg"; // Default for standard RGB images
    } else if (hint.toLowerCase().contains("indexed") || hint.toLowerCase().contains("paletted")) {
      return "png"; // Indexed color is typically PNG
    }

    log.warn("Could not determine format from BufferedImage: {}", img.getClass().getName());
    return "";
  }

  /** Converts a base64-encoded WebP image string to JPEG byte array. */
  public byte[] convertBase64WebpToJpeg(String base64Data) {
    try {
      Base64.Decoder decoder = java.util.Base64.getDecoder();
      byte[] imageData = decoder.decode(base64Data.trim());

      // Convert WebP to JPEG (with fallback for other formats)
      return convertImage(imageData, "image/webp", "image/jpeg");
    } catch (IllegalArgumentException e) {
      log.error("Failed to decode base64 image: {}", e.getMessage(), e);
      throw new RuntimeException(
          "Invalid base64-encoded image data. Ensure the string is properly base64 encoded.", e);
    } catch (Exception e) {
      log.error("Error converting base64 WebP: {}", e.getMessage(), e);
      throw new RuntimeException(
          "Failed to convert base64 image. Supported formats: JPEG, PNG, WebP", e);
    }
  }
}
