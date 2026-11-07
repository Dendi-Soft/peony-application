package com.dendi.peony.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Maps domain and request-validation exceptions to uniform {@code {"error": ...}} HTTP responses.
 *
 * <p>Covers the auth endpoints (401 invalid credentials, 409 duplicate credentials), the plant and
 * location endpoints (404 missing catalog plant, individual, house, room, or image), and request
 * validation (400 for invalid fields or arguments). The recognition endpoint keeps its own
 * 415/429/502 handlers for upstream PlantNet failures; its remaining errors flow through this
 * advice.
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
   * Maps a missing individual (or one owned by another user) to a 404 response.
   *
   * @param exception user-plant-not-found exception
   * @return 404 response with a consumer-facing message
   */
  @ExceptionHandler(UserPlantNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleUserPlantNotFound(
      UserPlantNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND)
        .body(Map.of("error", "User plant not found"));
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
   * Maps a missing personal photo (no row or file) to a 404 response.
   *
   * @param exception personal-image-not-found exception
   * @return 404 response with a consumer-facing message
   */
  @ExceptionHandler(UserPlantImageNotFoundException.class)
  public ResponseEntity<Map<String, String>> handleUserPlantImageNotFound(
      UserPlantImageNotFoundException exception) {
    return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "Image not found"));
  }

  /**
   * Maps a second photo upload for an individual to a 409 response; clients treat it as success.
   *
   * @param exception image-already-exists exception
   * @return 409 response with a consumer-facing message
   */
  @ExceptionHandler(ImageAlreadyExistsException.class)
  public ResponseEntity<Map<String, String>> handleImageAlreadyExists(
      ImageAlreadyExistsException exception) {
    return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "Image already exists"));
  }

  /**
   * Maps a JPEG-signature upload that cannot be decoded to a 400 response.
   *
   * @param exception corrupt-image exception
   * @return 400 response with a consumer-facing message
   */
  @ExceptionHandler(CorruptImageException.class)
  public ResponseEntity<Map<String, String>> handleCorruptImage(CorruptImageException exception) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST)
        .body(Map.of("error", "Image could not be decoded"));
  }

  /**
   * Maps an upload that exceeds the 1 MiB cap to a 413 response.
   *
   * @param exception image-too-large exception
   * @return 413 response with a consumer-facing message
   */
  @ExceptionHandler(ImageTooLargeException.class)
  public ResponseEntity<Map<String, String>> handleImageTooLarge(ImageTooLargeException exception) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(Map.of("error", exception.getMessage()));
  }

  /**
   * Maps a multipart upload rejected by the servlet size limit to a 413 response.
   *
   * <p>This also covers the recognition endpoint, whose own advice does not handle the multipart
   * parser's exception.
   *
   * @param exception multipart size-limit exception
   * @return 413 response with a consumer-facing message
   */
  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, String>> handleMaxUploadSize(
      MaxUploadSizeExceededException exception) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(Map.of("error", "Image must be at most 1 MB"));
  }

  /**
   * Maps a non-JPEG photo upload to a 415 response.
   *
   * @param exception unsupported-format exception
   * @return 415 response with a consumer-facing message
   */
  @ExceptionHandler(UnsupportedImageFormatException.class)
  public ResponseEntity<Map<String, String>> handleUnsupportedImageFormat(
      UnsupportedImageFormatException exception) {
    return ResponseEntity.status(HttpStatus.UNSUPPORTED_MEDIA_TYPE)
        .body(Map.of("error", exception.getMessage()));
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
