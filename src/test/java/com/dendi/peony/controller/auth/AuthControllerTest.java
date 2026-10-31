package com.dendi.peony.controller.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.AdjustableClock;
import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.User;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Tests for the auth endpoints at the REST seam, against a real PostgreSQL database.
 *
 * <p>Covers the JWT cutover (spec #17): signup and signin issue a token pair, bearer tokens
 * authenticate protected routes (including a cross-family route), refresh rotates single-use, and
 * expired access or refresh tokens are rejected. No session cookie may be issued.
 */
@Import({TestcontainersConfiguration.class, AuthControllerTest.TestClockConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final String PASSWORD = "supersecret1";

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  @Autowired private AdjustableClock clock;

  /** Test clock: fixed so token and refresh-token TTLs can be advanced deterministically. */
  @TestConfiguration(proxyBeanMethods = false)
  static class TestClockConfig {

    /**
     * Creates the adjustable test clock, taking precedence over the application clock by type.
     *
     * @return the clock starting at 2026-10-05
     */
    @Bean
    @Primary
    AdjustableClock testClock() {
      return new AdjustableClock(Instant.parse("2026-10-05T12:00:00Z"));
    }
  }

  /**
   * Signing up creates a free-tier account and returns a token pair; the access token authenticates
   * the profile endpoint, and no session cookie is issued.
   */
  @Test
  void testSignup_ReturnsTokenPair_WhenValidCredentialsProvided() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(signupBody("ada", "ada@example.com", PASSWORD, "Ada", "Lovelace")))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.profile.username").value("ada"))
            .andExpect(jsonPath("$.profile.tier").value("free"))
            .andExpect(jsonPath("$.profile.capabilities").value(hasItem("PLANTS_READ")))
            .andExpect(
                jsonPath("$.profile.capabilities").value(not(hasItem("RECOGNITION_COMPLETE"))))
            .andReturn();
    assertNull(result.getResponse().getCookie("JSESSIONID"), "no session cookie may be issued");

    Tokens tokens = tokens(result);
    mockMvc
        .perform(get("/api/v1/auth/profile").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("ada"));

    User stored = userRepository.findByUsername("ada").orElseThrow();
    assertEquals(UserTier.FREE, stored.getTier());
    assertFalse(stored.getPasswordHash().equals(PASSWORD), "password must not be stored plain");
    assertTrue(
        passwordEncoder.matches(PASSWORD, stored.getPasswordHash()),
        "stored hash must verify against the original password");
  }

  /** Signing up with a username or email that already exists is rejected with a 409. */
  @Test
  void testSignup_Returns409_WhenUsernameOrEmailAlreadyTaken() throws Exception {
    signup("grace", "grace@example.com");
    long before = userRepository.count();

    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(signupBody("grace", "other@example.com", PASSWORD, "Grace", "Hopper")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());

    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(signupBody("grace2", "grace@example.com", PASSWORD, "Grace", "Hopper")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());

    assertEquals(before, userRepository.count(), "no duplicate account may be created");
  }

  /** Signing up with missing or malformed credentials is rejected with a 400. */
  @Test
  void testSignup_Returns400_WhenRequestInvalid() throws Exception {
    long before = userRepository.count();

    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(signupBody("short", "not-an-email", "123", "Ada", "Lovelace")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());

    assertEquals(
        before, userRepository.count(), "no account may be created for an invalid request");
  }

  /**
   * Signing in with valid credentials returns a fresh token pair; the bearer authenticates the
   * profile endpoint and a cross-family protected route, and no session cookie is issued.
   */
  @Test
  void testSignin_ReturnsTokenPair_WhenCredentialsValid() throws Exception {
    signup("lin", "lin@example.com");

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/signin")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(signinBody("lin", PASSWORD)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.profile.username").value("lin"))
            .andReturn();
    assertNull(result.getResponse().getCookie("JSESSIONID"), "no session cookie may be issued");

    String bearer = bearer(tokens(result));
    mockMvc
        .perform(get("/api/v1/auth/profile").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tier").value("free"));
    mockMvc
        .perform(get("/api/v1/plants").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());
  }

  /** Signing in with a wrong password is rejected without issuing tokens. */
  @Test
  void testSignin_Returns401_WhenPasswordIsWrong() throws Exception {
    signup("katherine", "katherine@example.com");

    mockMvc
        .perform(
            post("/api/v1/auth/signin")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(signinBody("katherine", "wrongpassword1")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").exists())
        .andExpect(jsonPath("$.token").doesNotExist())
        .andExpect(jsonPath("$.refreshToken").doesNotExist());
  }

  /** A protected route without a bearer token answers with the standard 401 envelope. */
  @Test
  void testProtectedRoute_Returns401_WhenBearerMissing() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /** A protected route with a garbage bearer token answers with the standard 401 envelope. */
  @Test
  void testProtectedRoute_Returns401_WhenBearerInvalid() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/profile").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /**
   * Refreshing rotates the presented token: a fresh pair is returned, the new access token works,
   * and replaying the old refresh token is rejected.
   */
  @Test
  void testRefresh_ReturnsFreshPair_AndRejectsReplay() throws Exception {
    Tokens initial = signup("jane", "jane@example.com");
    // Move the clock so the reissued access token has a different iat/exp.
    clock.advance(Duration.ofMinutes(1));

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(refreshBody(initial.refreshToken())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.profile.username").value("jane"))
            .andReturn();
    Tokens rotated = tokens(result);

    assertNotEquals(initial.accessToken(), rotated.accessToken());
    assertNotEquals(initial.refreshToken(), rotated.refreshToken());
    mockMvc
        .perform(get("/api/v1/auth/profile").header(HttpHeaders.AUTHORIZATION, bearer(rotated)))
        .andExpect(status().isOk());

    // The just-rotated token is single-use.
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(refreshBody(initial.refreshToken())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /** Refreshing with an unknown token is rejected with the standard 401 envelope. */
  @Test
  void testRefresh_Returns401_WhenTokenUnknown() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(refreshBody("unknown-refresh-token")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /** An access token past its TTL no longer authenticates protected routes. */
  @Test
  void testAccessToken_Returns401_WhenExpired() throws Exception {
    Tokens tokens = signup("expired-access", "expired-access@example.com");

    clock.advance(Duration.ofHours(2));

    mockMvc
        .perform(get("/api/v1/auth/profile").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /** A refresh token past its 30-day TTL is rejected. */
  @Test
  void testRefreshToken_Returns401_WhenExpired() throws Exception {
    Tokens tokens = signup("expired-refresh", "expired-refresh@example.com");

    clock.advance(Duration.ofDays(31));

    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(refreshBody(tokens.refreshToken())))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /** The profile endpoint reports the authenticated user and their current tier. */
  @Test
  void testProfile_ReturnsCurrentTier_WhenAuthenticated() throws Exception {
    Tokens tokens = signup("alan", "alan@example.com");

    mockMvc
        .perform(get("/api/v1/auth/profile").header(HttpHeaders.AUTHORIZATION, bearer(tokens)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alan"))
        .andExpect(jsonPath("$.email").value("alan@example.com"))
        .andExpect(jsonPath("$.tier").value("free"))
        .andExpect(jsonPath("$.photoUploadsPerMonth").value(5))
        .andExpect(jsonPath("$.capabilities").value(hasItem("RECOGNITION_BASIC")));
  }

  /**
   * Upgrading a free-tier account moves it to premium: the response and the profile report the
   * premium tier, the complete-recognition and premium capability flags, and no upload limit.
   */
  @Test
  void testUpgrade_MovesUserToPremium_WhenFreeTierUserUpgrades() throws Exception {
    Tokens tokens = signup("margaret", "margaret@example.com");
    String bearer = bearer(tokens);

    mockMvc
        .perform(post("/api/v1/subscription/upgrade").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tier").value("premium"))
        .andExpect(jsonPath("$.photoUploadsPerMonth").value(nullValue()))
        .andExpect(jsonPath("$.capabilities").value(hasItem("RECOGNITION_COMPLETE")))
        .andExpect(jsonPath("$.capabilities").value(hasItem("MULTI_DEVICE_SYNC")))
        .andExpect(jsonPath("$.capabilities").value(hasItem("ADVANCED_ANALYTICS")))
        .andExpect(jsonPath("$.capabilities").value(not(hasItem("RECOGNITION_BASIC"))));

    mockMvc
        .perform(get("/api/v1/auth/profile").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tier").value("premium"));
  }

  /** Upgrading an already-premium account is a no-op that keeps the account premium. */
  @Test
  void testUpgrade_KeepsPremium_WhenAlreadyPremium() throws Exception {
    String bearer = bearer(signup("hedy", "hedy@example.com"));

    mockMvc
        .perform(post("/api/v1/subscription/upgrade").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/subscription/upgrade").header(HttpHeaders.AUTHORIZATION, bearer))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tier").value("premium"));
  }

  /** The upgrade endpoint requires a bearer token. */
  @Test
  void testUpgrade_Returns401_WhenNoBearer() throws Exception {
    mockMvc
        .perform(post("/api/v1/subscription/upgrade"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /** Token pair returned by signup/signin/refresh. */
  private record Tokens(String accessToken, String refreshToken) {}

  private Tokens signup(String username, String email) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/signup")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(signupBody(username, email, PASSWORD, "Test", "User")))
            .andExpect(status().isCreated())
            .andReturn();
    return tokens(result);
  }

  private static Tokens tokens(MvcResult result) throws Exception {
    JsonNode body = JSON.readTree(result.getResponse().getContentAsString());
    return new Tokens(body.get("token").asText(), body.get("refreshToken").asText());
  }

  private static String bearer(Tokens tokens) {
    return "Bearer " + tokens.accessToken();
  }

  private static String signinBody(String username, String password) {
    return """
        {"username":"%s","password":"%s"}"""
        .formatted(username, password);
  }

  private static String refreshBody(String refreshToken) {
    return """
        {"refreshToken":"%s"}"""
        .formatted(refreshToken);
  }

  private static String signupBody(
      String username, String email, String password, String firstName, String lastName) {
    return """
        {"username":"%s","email":"%s","password":"%s","firstName":"%s","lastName":"%s"}"""
        .formatted(username, email, password, firstName, lastName);
  }
}
