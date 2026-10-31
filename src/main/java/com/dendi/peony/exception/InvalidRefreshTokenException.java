package com.dendi.peony.exception;

/**
 * Thrown when a refresh token is unknown, already rotated, or expired (spec #17).
 *
 * <p>The internal message distinguishes the causes; the HTTP layer always answers with the same
 * {@code 401 {"error":"Authentication required"}} envelope so no oracle is exposed.
 */
public class InvalidRefreshTokenException extends RuntimeException {

  /**
   * Creates the exception.
   *
   * @param message internal explanation of the failure
   */
  public InvalidRefreshTokenException(String message) {
    super(message);
  }
}
