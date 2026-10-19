package com.dendi.peony.service.auth;

import com.dendi.peony.dto.auth.ProfileResponse;
import com.dendi.peony.dto.auth.SignupRequest;
import com.dendi.peony.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Authentication flow: signup, signin, profile, and tier upgrade.
 *
 * <p>Thin orchestration over {@link UserService}; the signin flow and tier state changes are added
 * to this service as the session-based MVP grows.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserService userService;

  /**
   * Creates a new free-tier account.
   *
   * @param request signup credentials
   * @return the profile of the created account
   */
  public ProfileResponse signup(SignupRequest request) {
    User user = userService.signup(request);
    return ProfileResponse.from(user);
  }
}
