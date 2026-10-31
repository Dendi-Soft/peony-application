package com.dendi.peony.controller.plant;

import com.dendi.peony.api.model.AssignPlantLocationRequest;
import com.dendi.peony.api.model.CreateHouseRequest;
import com.dendi.peony.api.model.CreateRoomRequest;
import com.dendi.peony.api.model.HouseResponse;
import com.dendi.peony.api.model.PlantLocationResponse;
import com.dendi.peony.api.model.RoomResponse;
import com.dendi.peony.service.auth.PeonyUserDetails;
import com.dendi.peony.service.plant.LocationService;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for the nested location hierarchy (issue #10 Phase 2).
 *
 * <p>Houses are user-owned top-level locations, rooms nest under houses, and a plant can be
 * assigned to several rooms. All routes live under the /api/v1/ prefix and are protected by bearer
 * auth; the hierarchy is not tier-gated.
 */
@Tag(name = "locations", description = "Nested house/room locations for plants")
@RestController
@RequiredArgsConstructor
public class LocationsController {

  private final LocationService locationService;

  /**
   * Creates a house for the plant's locations.
   *
   * @param plantId plant the house is created for
   * @param request house payload
   * @param principal the authenticated account
   * @return the created house
   */
  @Operation(summary = "Create a house for the plant's locations")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "House created"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "Plant not found")
      })
  @PostMapping(
      value = "/api/v1/plants/{plantId}/locations/houses",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<HouseResponse> createHouse(
      @PathVariable Long plantId,
      @RequestBody CreateHouseRequest request,
      @AuthenticationPrincipal PeonyUserDetails principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(locationService.createHouse(plantId, principal.userId(), request));
  }

  /**
   * Creates a room in a house owned by the calling user.
   *
   * @param houseId id of the house the room belongs to
   * @param request room payload
   * @param principal the authenticated account
   * @return the created room
   */
  @Operation(summary = "Create a room in a house")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Room created"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "House not found")
      })
  @PostMapping(
      value = "/api/v1/houses/{houseId}/rooms",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<RoomResponse> createRoom(
      @PathVariable Long houseId,
      @RequestBody CreateRoomRequest request,
      @AuthenticationPrincipal PeonyUserDetails principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(locationService.createRoom(houseId, principal.userId(), request));
  }

  /**
   * Assigns a room to the plant.
   *
   * @param plantId id of the plant
   * @param request assignment payload
   * @param principal the authenticated account
   * @return the created location with its nested house and room
   */
  @Operation(summary = "Assign a room to the plant")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Location assigned"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "Plant or room not found"),
        @ApiResponse(responseCode = "409", description = "Plant already assigned to the room")
      })
  @PostMapping(
      value = "/api/v1/plants/{plantId}/locations",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<PlantLocationResponse> assign(
      @PathVariable Long plantId,
      @RequestBody AssignPlantLocationRequest request,
      @AuthenticationPrincipal PeonyUserDetails principal) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(locationService.assign(plantId, principal.userId(), request.getRoomId()));
  }

  /**
   * Lists the plant's locations.
   *
   * @param plantId id of the plant
   * @param principal the authenticated account
   * @return the plant's locations, oldest first
   */
  @Operation(summary = "List the plant's locations")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The plant's locations"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token"),
        @ApiResponse(responseCode = "404", description = "Plant not found")
      })
  @GetMapping(
      value = "/api/v1/plants/{plantId}/locations",
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<PlantLocationResponse>> list(
      @PathVariable Long plantId, @AuthenticationPrincipal PeonyUserDetails principal) {
    return ResponseEntity.ok(locationService.list(plantId));
  }
}
