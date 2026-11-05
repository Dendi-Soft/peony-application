package com.dendi.peony.controller.plant;

import com.dendi.peony.service.auth.PeonyUserDetails;
import com.dendi.peony.service.plant.PlantImageService;
import com.dendi.peony.service.plant.StoredImage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints serving plant images (issue #10 Phase 3).
 *
 * <p>The thumbnail is the image at gallery position 0; gallery images are addressed by their
 * 0-based position. Content types are derived from the stored file name. All routes live under the
 * /api/v1/ prefix and are protected by bearer auth.
 */
@Tag(name = "images", description = "Plant image serving (thumbnail and gallery)")
@RestController
@RequiredArgsConstructor
public class PlantImagesController {

  private final PlantImageService plantImageService;

  /**
   * Serves the plant's thumbnail image.
   *
   * @param plantId id of the plant
   * @param principal the authenticated account
   * @return the thumbnail bytes with a content type derived from the file name
   */
  @Operation(summary = "Get the plant's thumbnail image")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The thumbnail image"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "Plant or image not found")
      })
  @GetMapping(value = "/api/v1/plants/{plantId}/thumb")
  public ResponseEntity<byte[]> thumb(
      @PathVariable Long plantId, @AuthenticationPrincipal PeonyUserDetails principal) {
    StoredImage image = plantImageService.thumb(plantId);
    return ImageResponses.ok(image);
  }

  /**
   * Serves a gallery image by position.
   *
   * @param plantId id of the plant
   * @param index 0-based gallery position
   * @param principal the authenticated account
   * @return the image bytes with a content type derived from the file name
   */
  @Operation(summary = "Get a gallery image by position")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The gallery image"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "Plant, image, or file not found")
      })
  @GetMapping(value = "/api/v1/plants/{plantId}/gallery/{index}")
  public ResponseEntity<byte[]> gallery(
      @PathVariable Long plantId,
      @PathVariable int index,
      @AuthenticationPrincipal PeonyUserDetails principal) {
    StoredImage image = plantImageService.galleryImage(plantId, index);
    return ImageResponses.ok(image);
  }
}
