package com.dendi.peony.controller.plant;

import com.dendi.peony.api.model.HouseWithRoomsResponse;
import com.dendi.peony.service.auth.PeonyUserDetails;
import com.dendi.peony.service.plant.LocationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoint for the authenticated caller's houses (issue #26).
 *
 * <p>Each house nests its rooms so the location pickers can render the whole house -> room tree
 * from one response. The caller comes from the bearer token, never from a request parameter.
 */
@Tag(name = "locations", description = "Nested house/room locations for plants")
@RestController
@RequestMapping("/api/v1/me/houses")
@RequiredArgsConstructor
public class MeHousesController {

  private final LocationService locationService;

  /**
   * Lists the authenticated caller's houses, each with its rooms nested.
   *
   * @param principal the authenticated account
   * @return the caller's houses with their rooms
   */
  @Operation(summary = "List the caller's houses")
  @ApiResponses(
      value = {
        @ApiResponse(
            responseCode = "200",
            description =
                "The caller's houses with their rooms, ordered case-insensitively by name"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
      })
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<HouseWithRoomsResponse>> list(
      @AuthenticationPrincipal PeonyUserDetails principal) {
    return ResponseEntity.ok(locationService.listHouses(principal.userId()));
  }
}
