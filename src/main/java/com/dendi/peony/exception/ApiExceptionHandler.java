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
}
