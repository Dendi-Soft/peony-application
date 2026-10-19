package com.dendi.peony.exception;

/** Raised when no house the calling user owns exists for the requested id. */
public class HouseNotFoundException extends RuntimeException {

  /**
   * Creates the exception for a missing house.
   *
   * @param houseId id of the missing house
   */
  public HouseNotFoundException(Long houseId) {
    super("House not found: " + houseId);
  }
}
