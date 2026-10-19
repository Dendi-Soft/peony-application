package com.dendi.peony.service.auth;

import com.dendi.peony.api.model.ProfileResponse;
import com.dendi.peony.api.model.SigninRequest;
import com.dendi.peony.api.model.SigninResponse;
import com.dendi.peony.api.model.SignupRequest;
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
 * Authentication flow: signup and session-backed signin.
 *
 * <p>Signin authenticates against the stored password hash and binds the resulting security context
 * to a server-side session; subsequent requests carry the session cookie. Profile reads and tier
 * upgrades go through {@link UserService} so the service stays a pure auth seam.
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
    return ProfileResponseMapper.from(user);
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
              new UsernamePasswordAuthenticationToken(
                  request.getUsername(), request.getPassword()));
    } catch (BadCredentialsException e) {
      throw new InvalidCredentialsException("Invalid username or password");
    }

    SecurityContext context = SecurityContextHolder.createEmptyContext();
    context.setAuthentication(result);
    SecurityContextHolder.setContext(context);
    securityContextRepository.saveContext(context, httpRequest, httpResponse);

    PeonyUserDetails principal = (PeonyUserDetails) result.getPrincipal();
    User user = userService.getById(principal.userId());
    SigninResponse response = new SigninResponse();
    response.setSessionId(httpRequest.getSession().getId());
    response.setProfile(ProfileResponseMapper.from(user));
    return response;
  }
}
