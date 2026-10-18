package com.dendi.peony.dto.plantrecognition;

import java.util.List;
import lombok.Data;

/** Request DTO for plant recognition. Supports both single image and batch processing. */
@Data
public class PlantRecognitionRequest {

  /** Single image request. */
  // CHECKSTYLE.OFF: Indentation|LeftCurlyEol
  @Data
  public static class SingleImage {
    private byte[] imageData;
    private String format; // "image/jpeg", "image/png", etc.
    private String mimeType;
    private List<String> languages; // Optional: preferred response languages (fr, en, de, es)

    /** Creates a new SingleImage instance. */
    public SingleImage(byte[] imageData, String format, String mimeType, List<String> languages) {
      this.imageData = imageData;
      this.format = format;
      this.mimeType = mimeType;
      this.languages = languages;
    }

    /** Creates request from multipart form data. */
    public static SingleImage fromMultipart(String imageDataBase64, String format) {
      return new SingleImage(
          decodeBase64(imageDataBase64),
          format,
          getMimeType(format),
          List.of("en", "fr") // Default languages
          );
    }

    private static byte[] decodeBase64(String base64) {
      // Simple Base64 decoding - in production use proper library
      try {
        return java.util.Base64.getDecoder().decode(base64);
      } catch (Exception e) {
        throw new IllegalArgumentException("Invalid base64 encoded image data", e);
      }
    }

    private static String getMimeType(String format) {
      switch (format.toLowerCase()) {
        case "jpeg":
          return "image/jpeg";
        case "jpg":
          return "image/jpeg";
        case "png":
          return "image/png";
        default:
          throw new IllegalArgumentException("Unsupported image format: " + format);
      }
    }
  } // CHECKSTYLE.ON: Indentation|LeftCurlyEol

  /** Batch image request. */
  // CHECKSTYLE.OFF: Indentation|LeftCurlyEol
  @Data
  public static class BatchImage {
    private List<SingleImage> images;
    private String batchName; // Optional: name for batch tracking
    private List<String> languages; // Optional: preferred response languages

    @Override
    public String toString() {
      return "BatchImage{"
          + "images="
          + (images != null ? images.size() : 0)
          + ", batchName='"
          + (batchName != null ? batchName : "")
          + "'"
          + "}";
    }
  } // CHECKSTYLE.ON: Indentation|LeftCurlyEol
}
