package com.dendi.peony.controller.auth;

import com.dendi.peony.dto.auth.ProfileResponse;
import com.dendi.peony.dto.auth.SigninRequest;
import com.dendi.peony.dto.auth.SigninResponse;
import com.dendi.peony.dto.auth.SignupRequest;
import com.dendi.peony.service.auth.AuthService;
import com.dendi.peony.service.auth.PeonyUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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
 * <p>Signup creates free-tier accounts; signin, profile, and upgrade follow the session-based MVP
 * design (issue #12).
 */
@Tag(name = "Auth", description = "Account signup, signin, and profile endpoints")
@RestController
@RequestMapping("/api/v1/auth")
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

  /**
   * Signs the account in and issues a server-side session.
   *
   * @param request signin credentials (username or email, password)
   * @param httpRequest the current HTTP request
   * @param httpResponse the current HTTP response
   * @return the issued session id and the authenticated profile
   */
  @Operation(
      summary = "Sign in",
      description = "Validates credentials and issues a server-side session.")
  @ApiResponses(
      value = {
        @ApiResponse(responseCode = "200", description = "Signed in, session issued"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
      })
  @PostMapping(value = "/signin", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<SigninResponse> signin(
      @Valid @RequestBody SigninRequest request,
      HttpServletRequest httpRequest,
      HttpServletResponse httpResponse) {
    return ResponseEntity.ok(authService.signin(request, httpRequest, httpResponse));
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
        @ApiResponse(responseCode = "401", description = "No valid session")
      })
  @GetMapping(value = "/profile", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<ProfileResponse> profile(
      @AuthenticationPrincipal PeonyUserDetails principal) {
    return ResponseEntity.ok(authService.profile(principal.userId()));
  }
}
