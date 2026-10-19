package com.dendi.peony.exception;

/** Thrown when signin is attempted with credentials that do not match any account. */
public class InvalidCredentialsException extends RuntimeException {

  /**
   * Creates the exception.
   *
   * @param message consumer-facing explanation
   */
  public InvalidCredentialsException(String message) {
    super(message);
  }
}
