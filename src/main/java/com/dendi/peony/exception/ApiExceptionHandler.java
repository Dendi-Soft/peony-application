package com.dendi.peony.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps domain and request-validation exceptions to uniform {@code {"error": ...}} HTTP responses.
 *
 * <p>Covers the auth endpoints (401 invalid credentials, 409 duplicate credentials), the plant and
 * location endpoints (404 missing plant, house, room, or image; 409 duplicate plant-room
 * assignment), and request validation (400 for invalid fields or arguments). The recognition
 * endpoint keeps its own 415/429/502 handlers for upstream PlantNet failures; its remaining errors
 * flow through this advice.
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
   * Rejects refresh attempts with an unknown, rotated, or expired token.
   *
   * <p>The internal cause is deliberately not echoed: every refresh failure answers with the same
   * envelope as a missing bearer token, so clients cannot use the response to probe token state.
   *
   * @param exception invalid-refresh-token exception
   * @return 401 response with the standard authentication envelope
   */
  @ExceptionHandler(InvalidRefreshTokenException.class)
  public ResponseEntity<Map<String, String>> handleInvalidRefreshToken(
      InvalidRefreshTokenException exception) {
    return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Authentication required"));
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
  public ResponseEntity<Map<String, String>> handlePlantNotFound(PlantNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Plant not found"));
  }

  /**
   * Maps a missing house (or a house owned by another user) to a 404 response.
   *
   * @param exception house-not-found exception
   * @return 404 response with a consumer-facing message
   */
  @ExceptionHandler(HouseNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleHouseNotFound(HouseNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "House not found"));
  }

  /**
   * Maps a missing room (or a room outside the user's houses) to a 404 response.
   *
   * @param exception room-not-found exception
   * @return 404 response with a consumer-facing message
   */
  @ExceptionHandler(RoomNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleRoomNotFound(RoomNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Room not found"));
  }

  /**
   * Maps a duplicate plant-room assignment to a 409 response.
   *
   * @param exception duplicate-location exception
   * @return 409 response with a consumer-facing message
   */
  @ExceptionHandler(DuplicateLocationException.class)
  public ResponseEntity<Map<String, String>> handleDuplicateLocation(
      DuplicateLocationException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("error", "Plant is already assigned to the room"));
  }

  /**
   * Maps a missing plant image (no row at the position, or the file absent on disk) to a 404.
   *
   * @param exception image-not-found exception
   * @return 404 response with a consumer-facing message
   */
  @ExceptionHandler(ImageNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleImageNotFound(ImageNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Image not found"));
  }

  /**
   * Maps request-level validation failures (missing or invalid required fields) to a 400 response.
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
