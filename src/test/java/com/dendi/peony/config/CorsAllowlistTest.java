package com.dendi.peony.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * CORS allowlist tests through the real Spring Security filter chain (issue #24).
 *
 * <p>Runs under the local profile, which allows the Expo web dev server at {@code
 * http://localhost:8081}. Covers an allowed actual request, an allowed preflight, and rejected
 * origins for both actual and preflight requests.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
class CorsAllowlistTest {

  private static final String ALLOWED_ORIGIN = "http://localhost:8081";
  private static final String DISALLOWED_ORIGIN = "https://evil.example";

  @Autowired private MockMvc mockMvc;

  /**
   * An allowed origin on a protected route without a token gets the CORS origin header alongside
   * the usual 401, and no credentials header is advertised.
   */
  @Test
  void testAllowedOrigin_AddsCorsOriginHeader_OnUnauthenticatedProtectedRoute() throws Exception {
    mockMvc
        .perform(get("/api/v1/plants").header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN))
        .andExpect(status().isUnauthorized())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
  }

  /**
   * A preflight from an allowed origin succeeds without a bearer token and advertises the allowed
   * methods, headers and cache lifetime.
   */
  @Test
  void testAllowedPreflight_SucceedsWithoutToken_AndAdvertisesMethodsAndHeaders() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/plants")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                .header(
                    HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS,
                    "Authorization, Content-Type, Accept"))
        .andExpect(status().isOk())
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("GET")))
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("POST")))
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("PATCH")))
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, containsString("DELETE")))
        .andExpect(
            header()
                .string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Authorization")))
        .andExpect(
            header()
                .string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Content-Type")))
        .andExpect(
            header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, containsString("Accept")))
        .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_MAX_AGE, "3600"))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
  }

  /** A disallowed origin gets no CORS headers on an actual request. */
  @Test
  void testDisallowedOrigin_ReturnsNoCorsHeaders_OnProtectedRoute() throws Exception {
    mockMvc
        .perform(get("/api/v1/plants").header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
  }

  /** A preflight from a disallowed origin is rejected and gets no CORS headers. */
  @Test
  void testDisallowedPreflight_ReturnsNoCorsHeaders() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/plants")
                .header(HttpHeaders.ORIGIN, DISALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(status().isForbidden())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }
}
