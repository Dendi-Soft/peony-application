package com.dendi.peony.exception;

/** Raised when no individual plant exists for the requested id under the calling user. */
public class UserPlantNotFoundException extends RuntimeException {

  /**
   * Creates the exception for a missing individual.
   *
   * @param userPlantId id of the missing individual
   */
  public UserPlantNotFoundException(Long userPlantId) {
    super("User plant not found: " + userPlantId);
  }
}
