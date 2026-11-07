package com.dendi.peony.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Tests the API error envelope at the handler seam, without a database: image conflicts, missing
 * photos, and oversized uploads answer with their contract statuses rather than escaping.
 */
class ApiExceptionHandlerTest {

  private final MockMvc mockMvc =
      MockMvcBuilders.standaloneSetup(new ConflictController())
          .setControllerAdvice(new ApiExceptionHandler())
          .build();

  /** A second photo upload maps to the contract's 409 so the client can treat it as success. */
  @Test
  void testImageAlreadyExists_Returns409_WhenIndividualAlreadyHasImage() throws Exception {
    mockMvc
        .perform(post("/test/image-conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("Image already exists"));
  }

  /** A missing personal photo maps to a 404. */
  @Test
  void testUserPlantImageNotFound_Returns404_WhenPhotoMissing() throws Exception {
    mockMvc
        .perform(post("/test/image-missing"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Image not found"));
  }

  /** The servlet multipart cap maps to a 413 with the same message as the service-level check. */
  @Test
  void testMaxUploadSize_Returns413_WhenMultipartUploadExceedsCap() throws Exception {
    mockMvc
        .perform(post("/test/upload-too-large"))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.error").value("Image must be at most 1 MB"));
  }

  /** Stands in for the image services at the point where their exceptions fire. */
  @RestController
  static class ConflictController {

    @PostMapping("/test/image-conflict")
    void uploadDuplicateImage() {
      throw new ImageAlreadyExistsException(7L);
    }

    @PostMapping("/test/image-missing")
    void missingImage() {
      throw new UserPlantImageNotFoundException(7L);
    }

    @PostMapping("/test/upload-too-large")
    void oversizeUpload() {
      throw new MaxUploadSizeExceededException(1_048_576L);
    }
  }
}
