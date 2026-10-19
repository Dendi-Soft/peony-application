package com.dendi.peony.exception;

/** Thrown when a photo upload is rejected because the user's monthly quota is exhausted. */
public class UploadLimitExceededException extends RuntimeException {

  /**
   * Creates the exception.
   *
   * @param message consumer-facing explanation of the exhausted quota
   */
  public UploadLimitExceededException(String message) {
    super(message);
  }
}
