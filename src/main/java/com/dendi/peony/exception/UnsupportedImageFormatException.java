package com.dendi.peony.exception;

/** Thrown when uploaded image bytes cannot be decoded as a supported image format. */
public class UnsupportedImageFormatException extends RuntimeException {

  /**
   * Creates the exception with a detail message.
   *
   * @param message human-readable explanation of the problem
   */
  public UnsupportedImageFormatException(String message) {
    super(message);
  }

  /**
   * Creates the exception with a detail message and a cause.
   *
   * @param message human-readable explanation of the problem
   * @param cause underlying decode failure
   */
  public UnsupportedImageFormatException(String message, Throwable cause) {
    super(message, cause);
  }
}
