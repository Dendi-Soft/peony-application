package com.dendi.peony.controller.plant;

import com.dendi.peony.api.model.CreateUserPlantRequest;
import com.dendi.peony.api.model.UpdateUserPlantRequest;
import com.dendi.peony.api.model.UserPlantResponse;
import com.dendi.peony.entity.User;
import com.dendi.peony.service.auth.PeonyUserDetails;
import com.dendi.peony.service.auth.UserService;
import com.dendi.peony.service.plant.ImageSize;
import com.dendi.peony.service.plant.UserPlantImageService;
import com.dendi.peony.service.plant.UserPlantService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * REST endpoints for the authenticated caller's individual plants (issue #37).
 *
 * <p>The caller comes from the bearer token, never from a request body or parameter, and every
 * route is scoped to caller-owned rows. Responses are shaped by the caller's tier per
 * docs/api-tier-contracts.md.
 */
@Tag(name = "plants", description = "The caller's individual plants")
@RestController
@RequestMapping("/api/v1/me/plants")
@RequiredArgsConstructor
public class MePlantsController {

  private final UserPlantService userPlantService;
  private final UserPlantImageService userPlantImageService;
  private final UserService userService;

  /**
   * Lists the authenticated caller's individuals, newest first.
   *
   * @param principal the authenticated account
   * @return the caller's individuals, tier-shaped
   */
  @Operation(summary = "List the caller's plants")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "One row per individual, newest first, tier-shaped"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
      })
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<UserPlantResponse>> list(
      @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    return ResponseEntity.ok(userPlantService.list(user.getId(), user.getTier()));
  }

  /**
   * Adds an individual for an existing catalog plant.
   *
   * @param request individual payload
   * @param principal the authenticated account
   * @return the created individual
   */
  @Operation(summary = "Add one of the caller's plants")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Individual created"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "Plant or room not found")
      })
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserPlantResponse> create(
      @RequestBody CreateUserPlantRequest request,
      @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userPlantService.create(user.getId(), user.getTier(), request));
  }

  /**
   * Returns one caller-owned individual.
   *
   * @param userPlantId individual id
   * @param principal the authenticated account
   * @return the individual, tier-shaped
   */
  @Operation(summary = "Get one of the caller's plants")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The individual, tier-shaped"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "User plant not found")
      })
  @GetMapping(value = "/{userPlantId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserPlantResponse> get(
      @PathVariable Long userPlantId, @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    return ResponseEntity.ok(userPlantService.get(userPlantId, user.getId(), user.getTier()));
  }

  /**
   * Replaces one caller-owned individual's editable state.
   *
   * @param userPlantId individual id
   * @param request new individual state
   * @param principal the authenticated account
   * @return the updated individual
   */
  @Operation(
      summary = "Replace one of the caller's plants",
      description =
          "Replaces the editable fields; every optional field is treated as the new state, so a "
              + "null nickname clears it and a null roomId clears the location.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The updated individual"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "User plant or room not found")
      })
  @PatchMapping(
      value = "/{userPlantId}",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserPlantResponse> update(
      @PathVariable Long userPlantId,
      @RequestBody UpdateUserPlantRequest request,
      @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    return ResponseEntity.ok(
        userPlantService.update(userPlantId, user.getId(), user.getTier(), request));
  }

  /**
   * Deletes one caller-owned individual.
   *
   * @param userPlantId individual id
   * @param principal the authenticated account
   * @return 204 on success
   */
  @Operation(summary = "Delete one of the caller's plants")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "204", description = "Individual deleted"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "User plant not found")
      })
  @DeleteMapping("/{userPlantId}")
  public ResponseEntity<Void> delete(
      @PathVariable Long userPlantId, @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    userPlantService.delete(userPlantId, user.getId());
    return ResponseEntity.noContent().build();
  }

  /**
   * Records that the caller watered one of their individuals.
   *
   * @param userPlantId individual id
   * @param principal the authenticated account
   * @return the updated individual
   */
  @Operation(summary = "Water one of the caller's plants")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description = "Watering recorded; the next due date is recomputed"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "User plant not found")
      })
  @PostMapping(value = "/{userPlantId}/water", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserPlantResponse> water(
      @PathVariable Long userPlantId, @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    return ResponseEntity.ok(userPlantService.water(userPlantId, user.getId(), user.getTier()));
  }

  /**
   * Stores the caller's prepared JPEG photo for one of their individuals.
   *
   * <p>The upload is owner-checked and does not consume the recognition quota. A second upload for
   * an individual that already has a photo is a 409, which clients treat as success and refresh.
   *
   * @param userPlantId individual id
   * @param file multipart JPEG, at most 1 MiB and 40,000,000 pixels
   * @param principal the authenticated account
   * @return 201 with the updated individual carrying hasImage and imageId
   */
  @Operation(summary = "Attach the individual's personal photo")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "201",
            description = "Image stored; the updated individual carries hasImage and imageId"),
        @ApiResponse(responseCode = "400", description = "The JPEG cannot be decoded"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "User plant not found"),
        @ApiResponse(responseCode = "409", description = "The individual already has an image"),
        @ApiResponse(
            responseCode = "413",
            description = "The upload exceeds 1 MiB or the 40,000,000-pixel dimension cap"),
        @ApiResponse(responseCode = "415", description = "The upload is not a JPEG")
      })
  @PostMapping(
      value = "/{userPlantId}/image",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<UserPlantResponse> uploadImage(
      @PathVariable Long userPlantId,
      @RequestParam("file") MultipartFile file,
      @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    userPlantImageService.upload(userPlantId, user.getId(), file);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(userPlantService.get(userPlantId, user.getId(), user.getTier()));
  }

  /**
   * Serves the caller's display image for one of their individuals.
   *
   * @param userPlantId individual id
   * @param imageId immutable client-side cache version; accepted and ignored
   * @param principal the authenticated account
   * @return the display JPEG bytes, private-cacheable
   */
  @Operation(summary = "Get the individual's personal photo")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The display image"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "Individual or image not found")
      })
  @GetMapping(value = "/{userPlantId}/image", produces = MediaType.IMAGE_JPEG_VALUE)
  public ResponseEntity<byte[]> image(
      @PathVariable Long userPlantId,
      @RequestParam(value = "imageId", required = false) String imageId,
      @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    return ImageResponses.privateOk(
        userPlantImageService.load(userPlantId, user.getId(), ImageSize.DISPLAY));
  }

  /**
   * Serves the caller's thumbnail for one of their individuals.
   *
   * @param userPlantId individual id
   * @param imageId immutable client-side cache version; accepted and ignored
   * @param principal the authenticated account
   * @return the thumbnail JPEG bytes, private-cacheable
   */
  @Operation(summary = "Get the individual's personal photo thumbnail")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The thumbnail image"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "Individual or image not found")
      })
  @GetMapping(value = "/{userPlantId}/image/thumb", produces = MediaType.IMAGE_JPEG_VALUE)
  public ResponseEntity<byte[]> imageThumb(
      @PathVariable Long userPlantId,
      @RequestParam(value = "imageId", required = false) String imageId,
      @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    return ImageResponses.privateOk(
        userPlantImageService.load(userPlantId, user.getId(), ImageSize.THUMBNAIL));
  }
}
