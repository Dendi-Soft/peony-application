package com.dendi.peony.controller.auth;

import com.dendi.peony.dto.auth.ProfileResponse;
import com.dendi.peony.dto.auth.SignupRequest;
import com.dendi.peony.service.auth.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for account authentication.
 *
 * <p>Signup creates free-tier accounts; signin, profile, and upgrade follow the session-based MVP
 * design (issue #12).
 */
@Tag(name = "Auth", description = "Account signup, signin, and profile endpoints")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

  private final AuthService authService;

  /**
   * Creates a new account on the free tier.
   *
   * @param request signup credentials
   * @return the profile of the created account
   */
  @Operation(
      summary = "Create a new account",
      description = "Creates a free-tier account. The password is stored hashed.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Account created"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "409", description = "Username or email already taken")
      })
  @PostMapping(value = "/signup", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ProfileResponse> signup(@Valid @RequestBody SignupRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
  }
}
