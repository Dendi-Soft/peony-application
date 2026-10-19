package com.dendi.peony.dto.plantrecognition;

import java.util.List;

/**
 * Input for a plant recognition request sent to the PlantNet API.
 *
 * <p>Bundles the uploaded image bytes with the upload options so callers pass one object instead of
 * the same group of values through several overloads.
 *
 * @param image primary image bytes
 * @param secondImage optional second image bytes for multi-image identification
 * @param thirdImage optional third image bytes for multi-image identification
 * @param format media type of the image bytes (e.g., "image/jpeg")
 * @param organs organ hints for detection (e.g., "leaf", "flower"); null or empty means auto
 * @param language preferred common-name language; null means the API default
 */
public record RecognitionRequest(
    byte[] image,
    byte[] secondImage,
    byte[] thirdImage,
    String format,
    List<String> organs,
    String language) {

  /**
   * Creates a single-image recognition request with default options.
   *
   * @param image image bytes
   * @param format media type of the image bytes
   * @return request with a single image and no optional parameters
   */
  public static RecognitionRequest single(byte[] image, String format) {
    return new RecognitionRequest(image, null, null, format, null, null);
  }
}
