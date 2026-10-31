package com.dendi.peony.controller.plantrecognition;

import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.RecognitionRequest;
import com.dendi.peony.entity.User;
import com.dendi.peony.exception.PlantNetApiException;
import com.dendi.peony.exception.UnsupportedImageFormatException;
import com.dendi.peony.exception.UploadLimitExceededException;
import com.dendi.peony.service.auth.PeonyUserDetails;
import com.dendi.peony.service.auth.UploadRateLimitService;
import com.dendi.peony.service.auth.UserService;
import com.dendi.peony.service.plantrecognition.PlantNetClient;
import com.dendi.peony.service.plantrecognition.RecognitionTierShaper;
import com.dendi.peony.service.plantrecognition.WebpConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST endpoints for plant recognition.
 *
 * <p>Accepts an image as a multipart upload or a base64 string and returns PlantNet recognition
 * results. WebP uploads are converted to JPEG before being sent to the PlantNet API.
 */
@Tag(name = "Plant Recognition", description = "Endpoints for recognizing plants from images")
@RestController
@RequestMapping("/api/v1/plantrecognition")
@RequiredArgsConstructor
public class PlantRecognitionController {

  private static final String WEBP_MEDIA_TYPE = "image/webp";
  private static final String WEBP_FORMAT = "webp";

  private final PlantNetClient plantNetClient;
  private final WebpConverter webpConverter;
  private final UploadRateLimitService uploadRateLimitService;
  private final UserService userService;
  private final RecognitionTierShaper recognitionTierShaper;

  /**
   * Recognizes a plant from an uploaded image.
   *
   * @param file multipart image file (alternative to base64Image)
   * @param base64Image base64-encoded image (alternative to file)
   * @param format media type of the image, e.g., "image/jpeg"
   * @param principal the authenticated account
   * @return recognition result with hits ordered best match first
   */
  @Operation(
      summary = "Recognize plant from image",
      description = "Upload an image (multipart file or base64) to identify the plant.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Recognition successful"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "415", description = "Unsupported image format"),
        @ApiResponse(
            responseCode = "429",
            description = "Upload limit exceeded (free tier) or PlantNet API rate limit exceeded"),
        @ApiResponse(responseCode = "502", description = "PlantNet API unavailable or failed")
      })
  @PostMapping(
      value = "/recognize",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE},
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PlantRecognitionResult> recognize(
      @Parameter(description = "Image file (JPEG, PNG or WebP)")
          @RequestParam(value = "file", required = false)
          MultipartFile file,
      @Parameter(description = "Base64-encoded image (alternative to file)")
          @RequestParam(value = "base64Image", required = false)
          String base64Image,
      @Parameter(description = "Media type of the image", example = "image/jpeg")
          @RequestParam(defaultValue = "image/jpeg")
          String format,
      @AuthenticationPrincipal PeonyUserDetails principal)
      throws IOException {
    User user = userService.currentUser(principal);
    uploadRateLimitService.recordUpload(user.getTier(), user.getId());

    byte[] imageBytes;
    try {
      imageBytes = decodeImage(file, base64Image);
    } catch (IOException e) {
      // A failed read of the upload is a client-side problem, not a PlantNet outage
      throw new IllegalArgumentException("Could not read the uploaded image", e);
    }
    String effectiveFormat = format;

    // Convert WebP to JPEG for PlantNet API compatibility
    if (isWebp(effectiveFormat)) {
      imageBytes = webpConverter.convertWebpToJpeg(imageBytes);
      effectiveFormat = MediaType.IMAGE_JPEG_VALUE;
    }

    RecognitionRequest request = new RecognitionRequest();
    request.setImage(imageBytes);
    request.setFormat(effectiveFormat);
    PlantRecognitionResult result = plantNetClient.recognize(request);
    return ResponseEntity.ok(recognitionTierShaper.shape(result, user.getTier()));
  }

  /**
   * Maps recognition failures to HTTP error responses.
   *
   * @param exception exception thrown while recognizing a plant
   * @return error response with a consumer-facing message
   */
  @ExceptionHandler(
      value = {
        UnsupportedImageFormatException.class,
        PlantNetApiException.class,
        UploadLimitExceededException.class
      })
  public ResponseEntity<Map<String, String>> handleRecognitionFailure(RuntimeException exception) {
    HttpStatus status;
    String message;

    if (exception instanceof PlantNetApiException apiError) {
      UpstreamError upstreamError = upstreamError(apiError);
      status = upstreamError.status();
      message = upstreamError.message();
    } else if (exception instanceof UploadLimitExceededException) {
      status = HttpStatus.TOO_MANY_REQUESTS;
      message = exception.getMessage();
    } else {
      status = HttpStatus.UNSUPPORTED_MEDIA_TYPE;
      message = exception.getMessage();
    }

    return ResponseEntity.status(status).body(Map.of("error", message == null ? "" : message));
  }

  /**
   * Maps network failures while calling the PlantNet API to an HTTP 502 response.
   *
   * @param exception network failure thrown by the PlantNet client
   * @return error response with a consumer-facing message
   */
  @ExceptionHandler(IOException.class)
  public ResponseEntity<Map<String, String>> handleNetworkFailure(IOException exception) {
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
        .body(Map.of("error", "PlantNet API is unreachable. Please try again later."));
  }

  /** Upstream PlantNet error mapped to an HTTP status and a consumer-facing message. */
  private record UpstreamError(HttpStatus status, String message) {}

  private byte[] decodeImage(MultipartFile file, String base64Image) throws IOException {
    boolean hasFile = file != null && !file.isEmpty();
    boolean hasBase64 = base64Image != null && !base64Image.isEmpty();

    if (hasFile) {
      return file.getBytes();
    }
    if (hasBase64) {
      return Base64.getDecoder().decode(base64Image.trim());
    }
    throw new IllegalArgumentException(
        "Either a file upload or a base64-encoded image is required.");
  }

  private static boolean isWebp(String format) {
    return WEBP_MEDIA_TYPE.equalsIgnoreCase(format) || WEBP_FORMAT.equalsIgnoreCase(format);
  }

  private static UpstreamError upstreamError(PlantNetApiException apiError) {
    int status = apiError.getStatusCode();
    if (status == 429) {
      return new UpstreamError(
          HttpStatus.TOO_MANY_REQUESTS,
          "PlantNet API rate limit exceeded. Please wait a moment and try again.");
    }
    if (status >= 500) {
      return new UpstreamError(
          HttpStatus.BAD_GATEWAY,
          "PlantNet API is temporarily unavailable. Please try again later.");
    }
    return new UpstreamError(
        HttpStatus.BAD_REQUEST, "PlantNet API rejected the request: " + apiError.getDetail());
  }
}
