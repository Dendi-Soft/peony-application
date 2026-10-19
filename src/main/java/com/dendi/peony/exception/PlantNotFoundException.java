package com.dendi.peony.exception;

/** Raised when no catalog plant exists for the requested id. */
public class PlantNotFoundException extends RuntimeException {

  /**
   * Creates the exception for a missing plant.
   *
   * @param plantId id of the missing plant
   */
  public PlantNotFoundException(Long plantId) {
    super("Plant not found: " + plantId);
  }
}
