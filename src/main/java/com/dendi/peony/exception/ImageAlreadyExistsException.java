package com.dendi.peony.exception;

/** Raised when an individual already has a personal photo; the retry is treated as success. */
public class ImageAlreadyExistsException extends RuntimeException {

  /**
   * Creates the exception for the individual that owns the existing image.
   *
   * @param userPlantId id of the individual
   */
  public ImageAlreadyExistsException(Long userPlantId) {
    super("Image already exists: user plant " + userPlantId);
  }
}
