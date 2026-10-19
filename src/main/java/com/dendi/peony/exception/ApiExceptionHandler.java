package com.dendi.peony.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps auth-layer exceptions to HTTP error responses.
 *
 * <p>Kept as a controller advice so the recognition endpoint keeps its own exception handlers
 * (upstream PlantNet failures) while auth failures get a uniform {@code {"error": ...}} shape.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

  /**
   * Rejects signup attempts with an already-taken username or email.
   *
   * @param exception duplicate-credentials exception
   * @return 409 response with a consumer-facing message
   */
  @ExceptionHandler(DuplicateCredentialsException.class)
  public ResponseEntity<Map<String, String>> handleDuplicateCredentials(
      DuplicateCredentialsException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", exception.getMessage()));
  }

  /**
   * Rejects signins with credentials that match no account.
   *
   * @param exception invalid-credentials exception
   * @return 401 response with a consumer-facing message
   */
  @ExceptionHandler(InvalidCredentialsException.class)
  public ResponseEntity<Map<String, String>> handleInvalidCredentials(
      InvalidCredentialsException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", exception.getMessage()));
  }

  /**
   * Maps request validation failures to a 400 response.
   *
   * @param exception validation exception carrying the field errors
   * @return 400 response naming the first invalid field
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, String>> handleValidation(
      MethodArgumentNotValidException exception) {
    String message =
        exception.getBindingResult().getFieldErrors().stream()
            .findFirst()
            .map(fieldError -> fieldError.getField() + ": " + fieldError.getDefaultMessage())
            .orElse("Invalid request");
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of("error", message));
  }

  /**
   * Maps a missing plant to a 404 response.
   *
   * @param exception plant-not-found exception
   * @return 404 response with a consumer-facing message
   */
  @ExceptionHandler(PlantNotFoundException.class)
  public ResponseEntity<Map<String, String>> handlePlantNotFound(
      PlantNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Plant not found"));
  }

  /**
   * Maps request-level validation failures (missing or invalid required fields) to a 400
   * response.
   *
   * @param exception validation exception carrying the consumer-facing message
   * @return 400 response with the message
   */
  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
    String message = exception.getMessage();
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", message == null || message.isBlank() ? "Invalid request" : message));
  }
}
