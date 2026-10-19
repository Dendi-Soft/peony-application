package com.dendi.peony.service.auth;

import com.dendi.peony.dto.auth.ProfileResponse;
import com.dendi.peony.dto.auth.SigninRequest;
import com.dendi.peony.dto.auth.SigninResponse;
import com.dendi.peony.dto.auth.SignupRequest;
import com.dendi.peony.entity.User;
import com.dendi.peony.exception.InvalidCredentialsException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Service;

/**
 * Authentication flow: signup, signin, profile, and tier upgrade.
 *
 * <p>Signin authenticates against the stored password hash and binds the resulting security context
 * to a server-side session; subsequent requests carry the session cookie.
 */
@Service
@RequiredArgsConstructor
public class AuthService {

  private final UserService userService;
  private final AuthenticationManager authenticationManager;
  private final SecurityContextRepository securityContextRepository;

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

  /**
   * Signs the account in and issues a server-side session.
   *
   * @param request signin credentials (username or email, password)
   * @param httpRequest the current HTTP request (session target)
   * @param httpResponse the current HTTP response (session cookie carrier)
   * @return the session id and the authenticated user's profile
   * @throws InvalidCredentialsException when the credentials match no account
   */
  public SigninResponse signin(
      SigninRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
    Authentication result;
    try {
      result =
          authenticationManager.authenticate(
              new UsernamePasswordAuthenticationToken(request.username(), request.password()));
    } catch (BadCredentialsException e) {
      throw new InvalidCredentialsException("Invalid username or password");
    }

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(result);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, httpRequest, httpResponse);

    PeonyUserDetails principal = (PeonyUserDetails) result.getPrincipal();
    User user = userService.getById(principal.userId());
    return new SigninResponse(httpRequest.getSession().getId(), ProfileResponse.from(user));
  }

  /**
   * Returns the profile of the authenticated account, loaded fresh so a recent tier upgrade is
   * reflected.
   *
   * @param userId id of the authenticated account
   * @return the profile with tier and capability flags
   */
  public ProfileResponse profile(Long userId) {
    return ProfileResponse.from(userService.getById(userId));
  }

  /**
   * Moves the authenticated account to the premium tier (a state change, not a checkout).
   *
   * @param userId id of the authenticated account
   * @return the updated profile
   */
  public ProfileResponse upgrade(Long userId) {
    return ProfileResponse.from(userService.upgradeToPremium(userId));
  }
}
