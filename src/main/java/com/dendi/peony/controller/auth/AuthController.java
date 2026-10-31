package com.dendi.peony.controller.auth;

import com.dendi.peony.api.model.AuthResponse;
import com.dendi.peony.api.model.ProfileResponse;
import com.dendi.peony.api.model.RefreshRequest;
import com.dendi.peony.api.model.SigninRequest;
import com.dendi.peony.api.model.SignupRequest;
import com.dendi.peony.service.auth.JwtAuthService;
import com.dendi.peony.service.auth.PeonyUserDetails;
import com.dendi.peony.service.auth.ProfileResponseMapper;
import com.dendi.peony.service.auth.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST endpoints for account authentication.
 *
 * <p>Signup, signin, and refresh issue a JWT access token plus an opaque refresh token; protected
 * routes accept the access token as {@code Authorization: Bearer <token>} (spec #17).
 */
@Tag(name = "Auth", description = "Account signup, signin, refresh, and profile endpoints")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

  private final JwtAuthService authService;
  private final UserService userService;

  /**
   * Creates a new account on the free tier and issues a token pair.
   *
   * @param request signup credentials
   * @return the access token, refresh token, and profile of the created account
   */
  @Operation(
      summary = "Create a new account",
      description =
          "Creates a free-tier account and returns the JWT access token, refresh token, and"
              + " profile. The password is stored hashed.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "201", description = "Account created, tokens issued"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "409", description = "Username or email already taken")
      })
  @PostMapping(value = "/signup", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthResponse> signup(@Valid @RequestBody SignupRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(authService.signup(request));
  }

  /**
   * Signs the account in and issues a token pair.
   *
   * @param request signin credentials (username or email, password)
   * @return the access token, refresh token, and profile
   */
  @Operation(
      summary = "Sign in",
      description = "Validates credentials and returns a JWT access token and refresh token.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Signed in, tokens issued"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
      })
  @PostMapping(value = "/signin", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthResponse> signin(@Valid @RequestBody SigninRequest request) {
    return ResponseEntity.ok(authService.signin(request));
  }

  /**
   * Exchanges a valid refresh token for a fresh token pair.
   *
   * @param request the refresh token presented by the client
   * @return a new access token, a new refresh token, and the profile
   */
  @Operation(
      summary = "Exchange a refresh token",
      description =
          "Rotates the presented refresh token (single use) and returns a fresh token pair.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "New token pair issued"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Invalid or revoked refresh token")
      })
  @PostMapping(value = "/refresh", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
    return ResponseEntity.ok(authService.refresh(request));
  }

  /**
   * Returns the authenticated user's profile with tier and capability flags.
   *
   * @param principal the authenticated account
   * @return the profile
   */
  @Operation(
      summary = "Current user profile",
      description = "Returns the signed-in user with tier.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Profile returned"),
        @ApiResponse(responseCode = "401", description = "Missing or invalid bearer token")
      })
  @GetMapping(value = "/profile", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ProfileResponse> profile(
      @AuthenticationPrincipal PeonyUserDetails principal) {
    return ResponseEntity.ok(ProfileResponseMapper.from(userService.currentUser(principal)));
  }
}
