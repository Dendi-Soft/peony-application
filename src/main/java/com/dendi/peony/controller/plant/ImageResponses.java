package com.dendi.peony.controller.plant;

import com.dendi.peony.service.plant.StoredImage;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;

/**
 * Builds image responses shared by the catalogue and personal-photo controllers.
 *
 * <p>The catalogue routes are public content and add no cache header; the owner-scoped personal
 * routes are private and always mark the response {@code Cache-Control: private}.
 */
public final class ImageResponses {

  private static final String PRIVATE_CACHE_CONTROL = "private";

  private ImageResponses() {}

  /**
   * Builds a catalogue image response with a content type derived from the file name.
   *
   * @param image stored image
   * @return a 200 response carrying the bytes and derived content type
   */
  public static ResponseEntity<byte[]> ok(StoredImage image) {
    return ResponseEntity.ok()
        .contentType(mediaTypeFor(image.filename(), MediaType.APPLICATION_OCTET_STREAM))
        .body(image.data());
  }

  /**
   * Builds a private personal-photo response with a content type derived from the file name.
   *
   * @param image stored image
   * @return a 200 response carrying the bytes, derived content type, and private cache header
   */
  public static ResponseEntity<byte[]> privateOk(StoredImage image) {
    return ResponseEntity.ok()
        .contentType(mediaTypeFor(image.filename(), MediaType.IMAGE_JPEG))
        .header(HttpHeaders.CACHE_CONTROL, PRIVATE_CACHE_CONTROL)
        .body(image.data());
  }

  private static MediaType mediaTypeFor(String filename, MediaType fallback) {
    return MediaTypeFactory.getMediaType(filename).orElse(fallback);
  }
}
