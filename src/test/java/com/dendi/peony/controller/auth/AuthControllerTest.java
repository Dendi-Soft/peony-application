package com.dendi.peony.controller.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.User;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests for the auth endpoints at the REST seam, against a real PostgreSQL database.
 *
 * <p>Covers signup (free-tier account creation, password hashing, uniqueness, validation), signin
 * (session issuance), and the profile endpoint. Each test uses its own account because the tests
 * share one database.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private PasswordEncoder passwordEncoder;

  /**
   * Signing up with valid credentials creates a free-tier account; the response reports the tier,
   * the free upload limit, and the free capability flags, and the password is stored hashed.
   */
  @Test
  void testSignup_CreatesFreeTierAccount_WhenValidCredentialsProvided() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(signupBody("ada", "ada@example.com", "supersecret1", "Ada", "Lovelace")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.username").value("ada"))
        .andExpect(jsonPath("$.email").value("ada@example.com"))
        .andExpect(jsonPath("$.firstName").value("Ada"))
        .andExpect(jsonPath("$.tier").value("free"))
        .andExpect(jsonPath("$.photoUploadsPerMonth").value(5))
        .andExpect(jsonPath("$.capabilities").value(hasItem("PLANTS_READ")))
        .andExpect(jsonPath("$.capabilities").value(hasItem("PLANTS_CREATE")))
        .andExpect(jsonPath("$.capabilities").value(hasItem("PLANTS_UPDATE_NOTES")))
        .andExpect(jsonPath("$.capabilities").value(hasItem("RECOGNITION_BASIC")))
        .andExpect(jsonPath("$.capabilities").value(not(hasItem("RECOGNITION_COMPLETE"))))
        .andExpect(jsonPath("$.capabilities").value(not(hasItem("MULTI_DEVICE_SYNC"))))
        .andExpect(jsonPath("$.capabilities").value(not(hasItem("ADVANCED_ANALYTICS"))));

    User stored = userRepository.findByUsername("ada").orElseThrow();
    assertEquals(UserTier.FREE, stored.getTier());
    assertFalse(
        stored.getPasswordHash().equals("supersecret1"), "password must not be stored plain");
    assertTrue(
        passwordEncoder.matches("supersecret1", stored.getPasswordHash()),
        "stored hash must verify against the original password");
  }

  /**
   * Signing up with a username or email that already exists is rejected without creating a second
   * account.
   */
  @Test
  void testSignup_Returns409_WhenUsernameOrEmailAlreadyTaken() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(
                    signupBody("grace", "grace@example.com", "supersecret1", "Grace", "Hopper")))
        .andExpect(status().isCreated());
    long before = userRepository.count();

    // Same username, different email
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(
                    signupBody("grace", "other@example.com", "supersecret1", "Grace", "Hopper")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());

    // Same email, different username
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(
                    signupBody("grace2", "grace@example.com", "supersecret1", "Grace", "Hopper")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());

    assertEquals(before, userRepository.count(), "no duplicate account may be created");
  }

  /** Signing up with missing or malformed credentials is rejected with a 400 response. */
  @Test
  void testSignup_Returns400_WhenRequestInvalid() throws Exception {
    long before = userRepository.count();

    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(signupBody("short", "not-an-email", "123", "Ada", "Lovelace")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());

    assertEquals(
        before, userRepository.count(), "no account may be created for an invalid request");
  }

  /**
   * Signing in with valid credentials issues a server-side session; the response carries the
   * session id and the profile, and the session authenticates the profile endpoint.
   */
  @Test
  void testSignin_IssuesSession_WhenCredentialsValid() throws Exception {
    MockHttpSession session = signupAndSignin("lin", "lin@example.com");

    mockMvc
        .perform(get("/api/v1/auth/profile").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("lin"))
        .andExpect(jsonPath("$.tier").value("free"));
  }

  /** Signing in with a wrong password is rejected without issuing a session. */
  @Test
  void testSignin_Returns401_WhenPasswordIsWrong() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(
                    signupBody(
                        "katherine", "katherine@example.com", "supersecret1", "K", "Johnson")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/auth/signin")
                .contentType("application/json")
                .content(signinBody("katherine", "wrongpassword1")))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").exists());
  }

  /** The profile endpoint reports the authenticated user and their current tier. */
  @Test
  void testProfile_ReturnsCurrentTier_WhenAuthenticated() throws Exception {
    MockHttpSession session = signupAndSignin("alan", "alan@example.com");

    mockMvc
        .perform(get("/api/v1/auth/profile").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("alan"))
        .andExpect(jsonPath("$.email").value("alan@example.com"))
        .andExpect(jsonPath("$.tier").value("free"))
        .andExpect(jsonPath("$.photoUploadsPerMonth").value(5))
        .andExpect(jsonPath("$.capabilities").value(hasItem("RECOGNITION_BASIC")));
  }

  /** The profile endpoint requires a session. */
  @Test
  void testProfile_Returns401_WhenNoSession() throws Exception {
    mockMvc
        .perform(get("/api/v1/auth/profile"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /**
   * Upgrading a free-tier account moves it to premium: the response and the profile report the
   * premium tier, the complete-recognition and premium capability flags, and no upload limit.
   */
  @Test
  void testUpgrade_MovesUserToPremium_WhenFreeTierUserUpgrades() throws Exception {
    MockHttpSession session = signupAndSignin("margaret", "margaret@example.com");

    mockMvc
        .perform(post("/api/v1/subscription/upgrade").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tier").value("premium"))
        .andExpect(jsonPath("$.photoUploadsPerMonth").value(nullValue()))
        .andExpect(jsonPath("$.capabilities").value(hasItem("RECOGNITION_COMPLETE")))
        .andExpect(jsonPath("$.capabilities").value(hasItem("MULTI_DEVICE_SYNC")))
        .andExpect(jsonPath("$.capabilities").value(hasItem("ADVANCED_ANALYTICS")))
        .andExpect(jsonPath("$.capabilities").value(not(hasItem("RECOGNITION_BASIC"))));

    // The tier change is visible on the profile endpoint
    mockMvc
        .perform(get("/api/v1/auth/profile").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tier").value("premium"));
  }

  /** Upgrading an already-premium account is a no-op that keeps the account premium. */
  @Test
  void testUpgrade_KeepsPremium_WhenAlreadyPremium() throws Exception {
    MockHttpSession session = signupAndSignin("hedy", "hedy@example.com");

    mockMvc
        .perform(post("/api/v1/subscription/upgrade").session(session))
        .andExpect(status().isOk());
    mockMvc
        .perform(post("/api/v1/subscription/upgrade").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.tier").value("premium"));
  }

  /** The upgrade endpoint requires a session. */
  @Test
  void testUpgrade_Returns401_WhenNoSession() throws Exception {
    mockMvc
        .perform(post("/api/v1/subscription/upgrade"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /**
   * Signs up an account and signs it in against the given session, asserting the session id and the
   * free tier in the signin response.
   *
   * @param username account username to create
   * @param email account email to create
   * @return the signed-in session
   * @throws Exception when the HTTP exchange fails
   */
  private MockHttpSession signupAndSignin(String username, String email) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType("application/json")
                .content(signupBody(username, email, "supersecret1", "Ada", "Lovelace")))
        .andExpect(status().isCreated());

    MockHttpSession session = new MockHttpSession();
    mockMvc
        .perform(
            post("/api/v1/auth/signin")
                .session(session)
                .contentType("application/json")
                .content(signinBody(username, "supersecret1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.sessionId").value(session.getId()))
        .andExpect(jsonPath("$.profile.username").value(username))
        .andExpect(jsonPath("$.profile.tier").value("free"));
    return session;
  }

  private static String signinBody(String username, String password) {
    return """
        {"username":"%s","password":"%s"}"""
        .formatted(username, password);
  }

  private static String signupBody(
      String username, String email, String password, String firstName, String lastName) {
    return """
        {"username":"%s","email":"%s","password":"%s","firstName":"%s","lastName":"%s"}"""
        .formatted(username, email, password, firstName, lastName);
  }
}
