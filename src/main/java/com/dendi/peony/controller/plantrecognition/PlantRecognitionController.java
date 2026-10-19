package com.dendi.peony.controller.plantrecognition;

import com.dendi.peony.dto.plantrecognition.PlantRecognitionResult;
import com.dendi.peony.service.plantrecognition.PlantNetClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** REST controller for plant recognition endpoints using PlantNet API with caching. */
@Tag(name = "Plant Recognition", description = "Endpoints for recognizing plants from images")
@RestController
@RequestMapping("/api/plantrecognition")
@RequiredArgsConstructor
public class PlantRecognitionController {

  private final PlantNetClient plantNetClient;
  private final WebpConverter webpConverter;

  /** Uploads an image (multipart file or base64) and gets plant recognition results. */
  @Operation(
      summary = "Recognize Plant from Image",
      description = "Upload an image to identify the plant.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Recognition successful"),
        @ApiResponse(
            responseCode = "400",
            description = "Invalid request data or unsupported format"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
      })
  @PostMapping(
      value = "/recognize",
      consumes = {MediaType.MULTIPART_FORM_DATA_VALUE, MediaType.APPLICATION_JSON_VALUE},
      produces = "application/json")
  public ResponseEntity<?> recognize(
      @RequestParam(value = "file", required = false)
          org.springframework.web.multipart.MultipartFile file,
      @RequestParam(value = "base64Image", required = false) String base64Image,
      @RequestParam(defaultValue = "image/jpeg") String format) {

    boolean hasFile = file != null && !file.isEmpty();
    boolean hasBase64 = base64Image != null && !base64Image.isEmpty();

    byte[] imageBytes;

    if (hasFile) {
      try {
        imageBytes = file.getBytes();

        // Convert WebP to JPEG for PlantNet API compatibility
        if ("webp".equalsIgnoreCase(format.toLowerCase())) {
          webpConverter.convertImage(imageBytes, "image/webp", "image/jpeg");
        }
      } catch (Exception e) {
        throw new RuntimeException("Failed to process WebP image.", e);
      }

    } else if (hasBase64) {
      try {
        if ("webp".equalsIgnoreCase(format.toLowerCase())) {
          imageBytes = webpConverter.convertBase64WebpToJpeg(base64Image);
        } else {
          byte[] decodedBytes = java.util.Base64.getDecoder().decode(base64Image.trim());
          imageBytes = decodedBytes;
        }
      } catch (IllegalArgumentException e) {
        throw new RuntimeException("Invalid base64-encoded image data.", e);
      }

    } else {
      throw new IllegalArgumentException(
          "Either a file upload or a base64-encoded image is required.");
    }

    PlantRecognitionResult result;
    try {
      result = plantNetClient.recognize(imageBytes, format);
    } catch (Exception e) {
      // Provide helpful error message about rate limiting
      String errorMessage = e.getMessage();

      if (errorMessage != null
          && (errorMessage.contains("api-version")
              || errorMessage.toLowerCase().contains("rate"))) {
        throw new RuntimeException(
            "PlantNet API is currently rate limited or experiencing issues. Please wait a moment and try again.",
            e);
      }

      // Check for unsupported format errors
      if (errorMessage != null
          && (errorMessage.contains("webp") || errorMessage.toLowerCase().contains("format"))) {
        throw new RuntimeException(
            "Image format not supported by PlantNet API. Please use JPEG or PNG format.", e);
      }

      throw new RuntimeException("PlantNet recognition failed: " + e.getMessage(), e);
    }

    // Return the raw result - Spring will serialize it with all fields intact
    String query = null; // Empty/null since we don't have a specific species search yet

    return ResponseEntity.ok(result);
  }
}
