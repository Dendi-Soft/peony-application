package com.dendi.peony.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Returns a 401 JSON response when a request reaches a protected route without a valid session.
 *
 * <p>API consumers (mobile client, curl) get a machine-readable body instead of a browser login
 * redirect.
 */
public class UnauthorizedEntryPoint implements AuthenticationEntryPoint {

  /**
   * Writes the 401 response.
   *
   * @param request request that was rejected
   * @param response response to write the error body to
   * @param authException the failed authentication
   * @throws IOException when the response body cannot be written
   */
  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
    response.getWriter().write("{\"error\":\"Authentication required\"}");
  }
}
