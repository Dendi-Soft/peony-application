package com.dendi.peony.exception;

/** Raised when a plant is assigned to a room it is already in. */
public class DuplicateLocationException extends RuntimeException {

  /**
   * Creates the exception for a duplicate assignment.
   *
   * @param plantId id of the plant
   * @param roomId id of the room
   */
  public DuplicateLocationException(Long plantId, Long roomId) {
    super("Plant is already assigned to the room: plant " + plantId + ", room " + roomId);
  }
}
