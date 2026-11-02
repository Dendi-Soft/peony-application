package com.dendi.peony.controller.plant;

import com.dendi.peony.api.model.CreateRoomRequest;
import com.dendi.peony.api.model.RoomResponse;
import com.dendi.peony.service.auth.PeonyUserDetails;
import com.dendi.peony.service.plant.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for creating rooms under a house (issue #10 Phase 2).
 *
 * <p>Rooms nest under user-owned houses; the caller comes from the bearer token and the house is
 * scoped to the caller, so another user's house behaves as not found. The hierarchy is not
 * tier-gated.
 */
@Tag(name = "locations", description = "The caller's houses and their rooms")
@RestController
@RequiredArgsConstructor
public class LocationsController {

  private final LocationService locationService;

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
}
