package com.dendi.peony.exception;

/** Raised when a photo upload exceeds the 1 MiB cap shared with the recognition endpoint. */
public class ImageTooLargeException extends RuntimeException {

  /**
   * Creates the exception with a detail message.
   *
   * @param message consumer-facing explanation of the limit
   */
  public ImageTooLargeException(String message) {
    super(message);
  }
}
