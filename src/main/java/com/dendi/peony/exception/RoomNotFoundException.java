package com.dendi.peony.exception;

/** Raised when no room reachable through the calling user's houses exists for the requested id. */
public class RoomNotFoundException extends RuntimeException {

  /**
   * Creates the exception for a missing room.
   *
   * @param roomId id of the missing room
   */
  public RoomNotFoundException(Long roomId) {
    super("Room not found: " + roomId);
  }
}
