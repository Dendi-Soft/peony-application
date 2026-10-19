package com.dendi.peony.controller.subscription;

import com.dendi.peony.api.model.ProfileResponse;
import com.dendi.peony.service.auth.AuthService;
import com.dendi.peony.service.auth.PeonyUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for subscription state.
 *
 * <p>Upgrading is a tier state change only (issue #12): no payment or billing flow is involved.
 */
@Tag(name = "Subscription", description = "Subscription tier endpoints")
@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

  private final AuthService authService;

  /**
   * Moves the authenticated account to the premium tier.
   *
   * @param principal the authenticated account
   * @return the updated profile
   */
  @Operation(
      summary = "Upgrade to premium",
      description = "Moves the signed-in account to the premium tier. Idempotent.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Account is premium"),
        @ApiResponse(responseCode = "401", description = "No valid session")
      })
  @PostMapping(value = "/upgrade", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ProfileResponse> upgrade(
      @AuthenticationPrincipal PeonyUserDetails principal) {
    return ResponseEntity.ok(authService.upgrade(principal.userId()));
  }
}
