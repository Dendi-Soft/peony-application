package com.dendi.peony.controller.auth;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Tests for the auth endpoints at the REST seam, against a real PostgreSQL database.
 *
 * <p>Covers signup (free-tier account creation, password hashing, uniqueness, validation), and the
 * signin/profile/upgrade flows that build on it.
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
            post("/api/auth/signup")
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
            post("/api/auth/signup")
                .contentType("application/json")
                .content(
                    signupBody("grace", "grace@example.com", "supersecret1", "Grace", "Hopper")))
        .andExpect(status().isCreated());

    // Same username, different email
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType("application/json")
                .content(
                    signupBody("grace", "other@example.com", "supersecret1", "Grace", "Hopper")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());

    // Same email, different username
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType("application/json")
                .content(
                    signupBody("grace2", "grace@example.com", "supersecret1", "Grace", "Hopper")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").exists());

    assertEquals(1, userRepository.count(), "no duplicate account may be created");
  }

  /** Signing up with missing or malformed credentials is rejected with a 400 response. */
  @Test
  void testSignup_Returns400_WhenRequestInvalid() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType("application/json")
                .content(signupBody("short", "not-an-email", "123", "Ada", "Lovelace")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());

    assertEquals(0, userRepository.count(), "no account may be created for an invalid request");
  }

  private static String signupBody(
      String username, String email, String password, String firstName, String lastName) {
    return """
        {"username":"%s","email":"%s","password":"%s","firstName":"%s","lastName":"%s"}"""
        .formatted(username, email, password, firstName, lastName);
  }
}
