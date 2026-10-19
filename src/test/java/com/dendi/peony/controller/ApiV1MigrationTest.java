package com.dendi.peony.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Pins the ADR-004 route migration: the entire API lives under the /api/v1/ prefix and the
 * unversioned paths are gone. Behavior is unchanged; only the prefix moved.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class ApiV1MigrationTest {

  @Autowired private MockMvc mockMvc;

  /** The old unversioned paths no longer resolve, even for a signed-in user. */
  @Test
  void testOldRoutes_Return404_WhenV1PrefixShipped() throws Exception {
    MockHttpSession session = signupAndSignin("t10-mig-1");

    mockMvc
        .perform(
            post("/api/plantrecognition/recognize")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .param("base64Image", "AAAA")
                .session(session))
        .andExpect(status().isNotFound());
    mockMvc.perform(get("/api/auth/profile").session(session)).andExpect(status().isNotFound());
    mockMvc
        .perform(post("/api/subscription/upgrade").session(session))
        .andExpect(status().isNotFound());
  }

  /** The old auth paths lost their public status when they moved under /api/v1. */
  @Test
  void testOldAuthRoutes_RequireSession_WhenMovedUnderV1() throws Exception {
    mockMvc
        .perform(
            post("/api/auth/signup")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(signupBody("t10-mig-2")))
        .andExpect(status().isUnauthorized());
    mockMvc
        .perform(
            post("/api/auth/signin")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"username\":\"t10-mig-2\",\"password\":\"supersecret1\"}"))
        .andExpect(status().isUnauthorized());
  }

  /** The v1 paths serve the migrated routes, including the public auth endpoints. */
  @Test
  void testV1Routes_AreServed_WhenMigrated() throws Exception {
    // Public auth endpoints work without a session
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(signupBody("t10-mig-3")))
        .andExpect(status().isCreated());

    // Signed-in traffic reaches the migrated plant and auth routes
    MockHttpSession session = new MockHttpSession();
    mockMvc
        .perform(
            post("/api/v1/auth/signin")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"username\":\"t10-mig-3\",\"password\":\"supersecret1\"}"))
        .andExpect(status().isOk());
    mockMvc.perform(get("/api/v1/plants").session(session)).andExpect(status().isOk());
    mockMvc
        .perform(get("/api/v1/auth/profile").session(session))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.username").value("t10-mig-3"));
  }

  private static String signupBody(String username) {
    return """
        {"username":"%s","email":"%s@example.com","password":"supersecret1","firstName":"Test","lastName":"User"}"""
        .formatted(username, username);
  }

  private MockHttpSession signupAndSignin(String username) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(signupBody(username)))
        .andExpect(status().isCreated());

    MockHttpSession session = new MockHttpSession();
    mockMvc
        .perform(
            post("/api/v1/auth/signin")
                .session(session)
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"username\":\"%s\",\"password\":\"supersecret1\"}".formatted(username)))
        .andExpect(status().isOk());
    return session;
  }
}
