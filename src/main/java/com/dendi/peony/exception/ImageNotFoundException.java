package com.dendi.peony.exception;

/** Raised when a plant has no image at the requested position, or the file is missing on disk. */
public class ImageNotFoundException extends RuntimeException {

  /**
   * Creates the exception for a missing image.
   *
   * @param plantId id of the plant
   * @param position 0-based gallery position (or thumbnail position)
   */
  public ImageNotFoundException(Long plantId, Integer position) {
    super("Image not found: plant " + plantId + ", position " + position);
  }
}
