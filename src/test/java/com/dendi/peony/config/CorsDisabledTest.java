package com.dendi.peony.config;

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
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests that an empty CORS allowlist disables CORS (issue #24).
 *
 * <p>Runs under the default profile, whose base configuration has an empty allowlist. Even the
 * local profile's Expo origin must not be allowed here, proving the base configuration does not
 * inherit the local origin.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class CorsDisabledTest {

  private static final String EXPO_LOCAL_ORIGIN = "http://localhost:8081";

  @Autowired private MockMvc mockMvc;

  /** An actual request carrying an origin gets no CORS headers when the allowlist is empty. */
  @Test
  void testOriginRequest_ReturnsNoCorsHeaders_WhenAllowlistEmpty() throws Exception {
    mockMvc
        .perform(get("/api/v1/plants").header(HttpHeaders.ORIGIN, EXPO_LOCAL_ORIGIN))
        .andExpect(status().isUnauthorized())
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
  }

  /** A preflight request gets no CORS headers when the allowlist is empty. */
  @Test
  void testPreflight_ReturnsNoCorsHeaders_WhenAllowlistEmpty() throws Exception {
    mockMvc
        .perform(
            options("/api/v1/plants")
                .header(HttpHeaders.ORIGIN, EXPO_LOCAL_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
        .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
  }
}
