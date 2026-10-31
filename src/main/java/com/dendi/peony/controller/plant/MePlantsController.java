package com.dendi.peony.controller.plant;

import com.dendi.peony.api.model.UserPlantResponse;
import com.dendi.peony.entity.User;
import com.dendi.peony.service.auth.PeonyUserDetails;
import com.dendi.peony.service.auth.UserService;
import com.dendi.peony.service.plant.UserPlantService;
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
 * REST endpoint for the authenticated caller's plants (issue #25).
 *
 * <p>The caller comes from the bearer token, never from a request parameter. Rows are shaped by the
 * caller's tier per docs/api-tier-contracts.md.
 */
@Tag(name = "plants", description = "Plant records (typahead, registration, detail, notes)")
@RestController
@RequestMapping("/api/v1/me/plants")
@RequiredArgsConstructor
public class MePlantsController {

  private final UserPlantService userPlantService;
  private final UserService userService;

  /**
   * Lists the authenticated caller's user plants, newest first.
   *
   * @param principal the authenticated account
   * @return the caller's user plants, tier-shaped
   */
  @Operation(summary = "List the caller's plants")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "The caller's user plants, newest first"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
      })
  @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<UserPlantResponse>> list(
      @AuthenticationPrincipal PeonyUserDetails principal) {
    User user = userService.currentUser(principal);
    return ResponseEntity.ok(userPlantService.list(user.getId(), user.getTier()));
  }
}
