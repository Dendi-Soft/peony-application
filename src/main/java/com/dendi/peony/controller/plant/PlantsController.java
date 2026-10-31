package com.dendi.peony.controller.plant;

import com.dendi.peony.api.model.CreatePlantRequest;
import com.dendi.peony.api.model.PlantNotes;
import com.dendi.peony.api.model.PlantResponse;
import com.dendi.peony.entity.User;
import com.dendi.peony.service.auth.PeonyUserDetails;
import com.dendi.peony.service.auth.UserService;
import com.dendi.peony.service.plant.PlantService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the plant catalog (issue #10 Phase 1).
 *
 * <p>Typahead search, manual registration, tier-filtered detail, and per-plant notes. Responses
 * honor the tier field visibility from docs/api-tier-contracts.md; the notes endpoint maps to the
 * per-user user_plants.nickname column.
 */
@Tag(name = "plants", description = "Plant records (typahead, registration, detail, notes)")
@RestController
@RequestMapping("/api/v1/plants")
@RequiredArgsConstructor
public class PlantsController {

  private final PlantService plantService;
  private final UserService userService;

  /**
   * Typahead search over names, scientific names, and tags.
   *
   * @param q query text, optional
   * @param principal the authenticated account
   * @return tier-filtered matches ordered by name
   */
  @Operation(summary = "Search plants (typahead)")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Matching plants, tier-filtered"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
      })
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<PlantResponse>> list(
      @RequestParam(required = false) String q,
      @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    return ResponseEntity.ok(plantService.search(q, user.getTier()));
  }

  /**
   * Registers a plant manually.
   *
   * @param request registration payload
   * @param principal the authenticated account
   * @return the created plant, tier-filtered
   */
  @Operation(summary = "Register a plant manually")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Plant created, tier-filtered"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
      })
  @PostMapping(
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PlantResponse> create(
      @RequestBody CreatePlantRequest request,
      @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(plantService.register(request, user.getId(), user.getTier()));
  }

  /**
   * Returns a plant.
   *
   * @param plantId catalog plant id
   * @param principal the authenticated account
   * @return the tier-filtered plant
   */
  @Operation(summary = "Get a plant")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Plant, tier-filtered"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "Plant not found")
      })
  @GetMapping(value = "/{plantId}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PlantResponse> get(
      @PathVariable Long plantId, @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    return ResponseEntity.ok(plantService.get(plantId, user.getTier()));
  }

  /**
   * Updates the user's note for a plant.
   *
   * @param plantId catalog plant id
   * @param notes note payload
   * @param principal the authenticated account
   * @return the updated note
   */
  @Operation(summary = "Update the per-plant notes")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Updated note"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "Plant not found")
      })
  @PatchMapping(
      value = "/{plantId}/notes",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PlantNotes> updateNotes(
      @PathVariable Long plantId,
      @RequestBody PlantNotes notes,
      @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    return ResponseEntity.ok(plantService.updateNotes(plantId, user.getId(), notes.getNotes()));
  }
}
