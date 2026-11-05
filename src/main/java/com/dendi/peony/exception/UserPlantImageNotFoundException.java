package com.dendi.peony.exception;

/** Raised when an individual has no personal photo, or its stored file is missing on disk. */
public class UserPlantImageNotFoundException extends RuntimeException {

  /**
   * Creates the exception for the individual without a retrievable image.
   *
   * @param userPlantId id of the individual
   */
  public UserPlantImageNotFoundException(Long userPlantId) {
    super("User plant image not found: " + userPlantId);
  }
}
