package com.dendi.peony.dto;

import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Standard error response format for API errors. */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ErrorResponse {

  private String code;
  private String message;
  private String details;
  private int statusCode;
  private LocalDateTime timestamp;

  /**
   * Creates an error response with status code and auto-generated timestamp.
   *
   * @param code Error classification (e.g., "VALIDATION", "NOT_FOUND", "SERVER_ERROR")
   * @param message Human-readable error message
   * @param statusCode HTTP status code
   */
  public ErrorResponse(String code, String message, int statusCode) {
    this(code, message, null, statusCode, LocalDateTime.now());
  }

  /**
   * Creates an error response with optional details and auto-generated timestamp.
   *
   * @param code Error classification (e.g., "VALIDATION", "NOT_FOUND", "SERVER_ERROR")
   * @param message Human-readable error message
   * @param details Optional additional context or stack trace information
   * @param statusCode HTTP status code
   */
  public ErrorResponse(String code, String message, String details, int statusCode) {
    this(code, message, details, statusCode, LocalDateTime.now());
  }
}
