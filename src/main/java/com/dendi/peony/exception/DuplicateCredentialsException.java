package com.dendi.peony.exception;

/** Thrown when signup is attempted with a username or email that is already taken. */
public class DuplicateCredentialsException extends RuntimeException {

  /**
   * Creates the exception.
   *
   * @param message consumer-facing explanation
   */
  public DuplicateCredentialsException(String message) {
    super(message);
  }
}
