package com.dendi.peony.config;

import com.dendi.peony.service.auth.PeonyUserDetails;
import com.dendi.peony.service.auth.PeonyUserDetailsService;
import com.dendi.peony.service.jwt.JwtTokenService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates requests carrying a valid bearer access token (spec #17).
 *
 * <p>Missing or invalid tokens leave the request unauthenticated; the configured entry point then
 * answers protected routes with the standard {@code 401 {"error":"Authentication required"}}
 * envelope.
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenService jwtTokenService;
  private final PeonyUserDetailsService userDetailsService;

  @Override
  protected void doFilterInternal(
      HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
      throws ServletException, IOException {
    String header = request.getHeader(HttpHeaders.AUTHORIZATION);
    if (header != null && header.startsWith(BEARER_PREFIX)) {
      authenticate(header.substring(BEARER_PREFIX.length()));
    }
    filterChain.doFilter(request, response);
  }

  /** Sets the security context when the token is valid; leaves it empty otherwise. */
  private void authenticate(String token) {
    try {
      Long userId = jwtTokenService.getUserIdFromAccessToken(token);
      PeonyUserDetails principal = userDetailsService.loadUserById(userId);
      UsernamePasswordAuthenticationToken authentication =
          new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
      SecurityContextHolder.getContext().setAuthentication(authentication);
    } catch (IllegalArgumentException | UsernameNotFoundException e) {
      SecurityContextHolder.clearContext();
    }
  }
}
