package com.dendi.peony.service.auth;

import com.dendi.peony.api.model.AuthResponse;
import com.dendi.peony.api.model.RefreshRequest;
import com.dendi.peony.api.model.SigninRequest;
import com.dendi.peony.api.model.SignupRequest;
import com.dendi.peony.entity.User;
import com.dendi.peony.exception.InvalidCredentialsException;
import com.dendi.peony.exception.InvalidRefreshTokenException;
import com.dendi.peony.service.jwt.JwtTokenService;
import com.dendi.peony.service.jwt.RefreshTokenService;
import com.dendi.peony.service.jwt.RefreshTokenService.IssuedRefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** JWT-based authentication service (spec #17). */
@Service
@RequiredArgsConstructor
public class JwtAuthService {

  private final UserService userService;
  private final AuthenticationManager authenticationManager;
  private final JwtTokenService jwtTokenService;
  private final RefreshTokenService refreshTokenService;

  /**
   * Creates a new free-tier account and issues a token pair.
   *
   * @param request signup credentials
   * @return the access token, refresh token, and profile
   */
  @Transactional
  public AuthResponse signup(SignupRequest request) {
    User user = userService.signup(request);
    return issueTokens(user);
  }

  /**
   * Signs the account in and issues a token pair.
   *
   * @param request signin credentials (username or email, password)
   * @return the access token, refresh token, and profile
   * @throws InvalidCredentialsException when the credentials do not match an account
   */
  @Transactional
  public AuthResponse signin(SigninRequest request) {
    Authentication result =
        authenticate(
            new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword()));

    PeonyUserDetails principal = (PeonyUserDetails) result.getPrincipal();
    User user = userService.getById(principal.userId());
    return issueTokens(user);
  }

  /**
   * Exchanges a valid refresh token for a fresh token pair (single-use rotation).
   *
   * @param request the refresh token presented by the client
   * @return the new access token, new refresh token, and profile
   * @throws InvalidRefreshTokenException when the token is unknown, revoked, or expired
   */
  @Transactional
  public AuthResponse refresh(RefreshRequest request) {
    IssuedRefreshToken rotated = refreshTokenService.rotateRefreshToken(request.getRefreshToken());
    User user = userService.getById(rotated.userId());
    String accessToken = jwtTokenService.createAccessToken(user.getId());
    return buildAuthResponse(user, accessToken, rotated.token());
  }

  /** Issues an access token and a stored opaque refresh token for the account. */
  private AuthResponse issueTokens(User user) {
    String accessToken = jwtTokenService.createAccessToken(user.getId());
    IssuedRefreshToken refresh = refreshTokenService.issueRefreshToken(user.getId());
    return buildAuthResponse(user, accessToken, refresh.token());
  }

  /** Authenticates credentials using the authentication manager. */
  private Authentication authenticate(UsernamePasswordAuthenticationToken token) {
    try {
      return authenticationManager.authenticate(token);
    } catch (AuthenticationException e) {
      throw new InvalidCredentialsException("Invalid username or password");
    }
  }

  /** Builds the shared auth response shape for signup, signin, and refresh. */
  private AuthResponse buildAuthResponse(User user, String accessToken, String refreshToken) {
    AuthResponse response = new AuthResponse();
    response.setToken(accessToken);
    response.setRefreshToken(refreshToken);
    response.setProfile(ProfileResponseMapper.from(user));
    return response;
  }
}
