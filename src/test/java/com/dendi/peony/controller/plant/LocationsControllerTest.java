package com.dendi.peony.controller.plant;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.House;
import com.dendi.peony.repository.HouseRepository;
import com.dendi.peony.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Tests for the room creation endpoint at the REST seam, against a real PostgreSQL database: rooms
 * nest under caller-owned houses only.
 *
 * <p>Houses are user-scoped: a house reachable only by another user behaves as not found. Tests
 * share one database, so each test uses its own account.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LocationsControllerTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private HouseRepository houseRepository;

  /** Location routes are protected: no bearer token means the standard 401 envelope. */
  @Test
  void testCreateRoom_Returns401_WhenNoBearer() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/houses/1/rooms")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"name\":\"Kitchen\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /** Creating a room under the caller's house returns the room with its house id. */
  @Test
  void testCreateRoom_CreatesRoom_WhenHouseOwnedByUser() throws Exception {
    String username = "t37-loc-room-1";
    String token = signupAndSignin(username);
    long houseId = seedHouse(username, "Home");

    mockMvc
        .perform(
            post("/api/v1/houses/" + houseId + "/rooms")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"name\":\"Kitchen\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.name").value("Kitchen"))
        .andExpect(jsonPath("$.houseId").value(houseId));
  }

  /** Room creation under an unknown house is a 404. */
  @Test
  void testCreateRoom_Returns404_WhenHouseNotFound() throws Exception {
    String token = signupAndSignin("t37-loc-room-404");

    mockMvc
        .perform(
            post("/api/v1/houses/9999999/rooms")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"name\":\"Kitchen\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("House not found"));
  }

  /**
   * Room creation under another user's house is a 404: rooms are scoped through house ownership.
   */
  @Test
  void testCreateRoom_Returns404_WhenHouseOwnedByOtherUser() throws Exception {
    signupAndSignin("t37-loc-room-owner");
    long houseId = seedHouse("t37-loc-room-owner", "Foreign home");
    String strangerToken = signupAndSignin("t37-loc-room-stranger");

    mockMvc
        .perform(
            post("/api/v1/houses/" + houseId + "/rooms")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"name\":\"Kitchen\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("House not found"));
  }

  /** A room payload without a name is rejected. */
  @Test
  void testCreateRoom_Returns400_WhenNameMissing() throws Exception {
    String username = "t37-loc-room-400";
    String token = signupAndSignin(username);
    long houseId = seedHouse(username, "Home");

    mockMvc
        .perform(
            post("/api/v1/houses/" + houseId + "/rooms")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  /** Seeds a house owned by the user and returns its id. */
  private long seedHouse(String username, String name) {
    House house = new House();
    house.setUserId(userRepository.findByUsername(username).orElseThrow().getId());
    house.setName(name);
    house.setCreatedAt(LocalDateTime.of(2026, 1, 1, 13, 0));
    return houseRepository.save(house).getId();
  }

  private String signupAndSignin(String username) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {"username":"%s","email":"%s@example.com","password":"supersecret1","firstName":"Test","lastName":"User"}"""
                        .formatted(username, username)))
        .andExpect(status().isCreated());

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/signin")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(
                        """
                        {"username":"%s","password":"supersecret1"}"""
                            .formatted(username)))
            .andExpect(status().isOk())
            .andReturn();
    return JSON.readTree(result.getResponse().getContentAsString()).get("token").asText();
  }
}
