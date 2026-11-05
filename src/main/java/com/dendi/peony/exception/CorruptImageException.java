package com.dendi.peony.exception;

/** Raised when uploaded bytes carry a JPEG signature but cannot be decoded into an image. */
public class CorruptImageException extends RuntimeException {

  /**
   * Creates the exception with a detail message.
   *
   * @param message human-readable explanation of the problem
   */
  public CorruptImageException(String message) {
    super(message);
  }
}
