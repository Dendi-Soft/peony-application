package com.dendi.peony.exception;

/**
 * Thrown when the PlantNet API responds with a non-successful HTTP status.
 *
 * <p>Carries the upstream status code and response detail so the controller can map the failure to
 * an appropriate HTTP response for API consumers.
 */
public class PlantNetApiException extends RuntimeException {

  private final int statusCode;
  private final String detail;

  /**
   * Creates the exception for an upstream PlantNet API error.
   *
   * @param statusCode HTTP status code returned by the PlantNet API
   * @param detail response body or short description of the failure
   */
  public PlantNetApiException(int statusCode, String detail) {
    super("PlantNet API error " + statusCode + ": " + detail);
    this.statusCode = statusCode;
    this.detail = detail;
  }

  /**
   * Returns the HTTP status code returned by the PlantNet API.
   *
   * @return upstream status code
   */
  public int getStatusCode() {
    return statusCode;
  }

  /**
   * Returns the upstream response body or failure description.
   *
   * @return upstream detail message
   */
  public String getDetail() {
    return detail;
  }
}
