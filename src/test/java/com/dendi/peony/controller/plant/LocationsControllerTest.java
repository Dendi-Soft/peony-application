package com.dendi.peony.controller.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.repository.HouseRepository;
import com.dendi.peony.repository.PlantLocationRepository;
import com.dendi.peony.repository.RoomRepository;
import com.dendi.peony.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * Tests for the nested location endpoints at the REST seam, against a real PostgreSQL database:
 * house creation under a plant, room creation under a house, room-to-plant assignment (multiple
 * locations, duplicate rejection), and listing a plant's locations.
 *
 * <p>Houses and rooms are user-scoped: a room reachable only through another user's house behaves
 * as not found. Tests share one database, so each test uses its own account and unique plant names.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class LocationsControllerTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private HouseRepository houseRepository;

  @Autowired private RoomRepository roomRepository;

  @Autowired private PlantLocationRepository plantLocationRepository;

  /** Location routes are protected: every endpoint rejects requests without a bearer token. */
  @Test
  void testCreateHouse_Returns401_WhenNoBearer() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/plants/1/locations/houses")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"name\":\"Home\"}"))
        .andExpect(status().isUnauthorized());
  }

  /** Location routes are protected: every endpoint rejects requests without a bearer token. */
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

  /** Location routes are protected: every endpoint rejects requests without a bearer token. */
  @Test
  void testAssignLocation_Returns401_WhenNoBearer() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/plants/1/locations")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"roomId\":1}"))
        .andExpect(status().isUnauthorized());
  }

  /** Location routes are protected: every endpoint rejects requests without a bearer token. */
  @Test
  void testListLocations_Returns401_WhenNoBearer() throws Exception {
    mockMvc.perform(get("/api/v1/plants/1/locations")).andExpect(status().isUnauthorized());
  }

  /** Creating a house stores it for the calling user and returns the house object. */
  @Test
  void testCreateHouse_CreatesHouseForUser_WhenPlantExists() throws Exception {
    String token = signupAndSignin("t10-loc-house-1");
    long plantId = registerPlant(token, "Ficus t10 loc house", "Ficus t10 loc house");

    mockMvc
        .perform(houseRequest(plantId, "Home").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.name").value("Home"));

    Long userId = userRepository.findByUsername("t10-loc-house-1").orElseThrow().getId();
    List<Long> houseIds =
        houseRepository.findByUserId(userId).stream().map(h -> h.getId()).toList();
    assertEquals(1, houseIds.size(), "the caller must own exactly one house");
  }

  /** House creation for an unknown plant is a 404. */
  @Test
  void testCreateHouse_Returns404_WhenPlantNotFound() throws Exception {
    String token = signupAndSignin("t10-loc-house-404");

    mockMvc
        .perform(houseRequest(9999999, "Home").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /** A house payload without a name is rejected. */
  @Test
  void testCreateHouse_Returns400_WhenNameMissing() throws Exception {
    String token = signupAndSignin("t10-loc-house-400");
    long plantId = registerPlant(token, "Ficus t10 loc house 400", "Ficus t10 loc house 400");

    mockMvc
        .perform(
            post("/api/v1/plants/" + plantId + "/locations/houses")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  /** Creating a room under the caller's house returns the room with its house id. */
  @Test
  void testCreateRoom_CreatesRoom_WhenHouseOwnedByUser() throws Exception {
    String token = signupAndSignin("t10-loc-room-1");
    long plantId = registerPlant(token, "Ficus t10 loc room", "Ficus t10 loc room");
    long houseId = createHouse(token, plantId, "Home");

    mockMvc
        .perform(
            roomRequest(houseId, "Kitchen").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.name").value("Kitchen"))
        .andExpect(jsonPath("$.houseId").value(houseId));
  }

  /** Room creation under an unknown house is a 404. */
  @Test
  void testCreateRoom_Returns404_WhenHouseNotFound() throws Exception {
    String token = signupAndSignin("t10-loc-room-404");

    mockMvc
        .perform(
            roomRequest(9999999, "Kitchen").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /**
   * Room creation under another user's house is a 404: rooms are scoped through house ownership.
   */
  @Test
  void testCreateRoom_Returns404_WhenHouseOwnedByOtherUser() throws Exception {
    String ownerToken = signupAndSignin("t10-loc-room-foreign-owner");
    long plantId = registerPlant(ownerToken, "Ficus t10 loc foreign", "Ficus t10 loc foreign");
    long houseId = createHouse(ownerToken, plantId, "Foreign home");

    String strangerToken = signupAndSignin("t10-loc-room-foreign-stranger");

    mockMvc
        .perform(
            roomRequest(houseId, "Kitchen")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /** A room payload without a name is rejected. */
  @Test
  void testCreateRoom_Returns400_WhenNameMissing() throws Exception {
    String token = signupAndSignin("t10-loc-room-400");
    long plantId = registerPlant(token, "Ficus t10 loc room 400", "Ficus t10 loc room 400");
    long houseId = createHouse(token, plantId, "Home");

    mockMvc
        .perform(
            post("/api/v1/houses/" + houseId + "/rooms")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  /** Assigning a room stores the assignment and returns the location with nested house and room. */
  @Test
  void testAssignLocation_CreatesAssignment_WhenRoomOwnedByUser() throws Exception {
    String token = signupAndSignin("t10-loc-assign-1");
    long plantId = registerPlant(token, "Ficus t10 loc assign", "Ficus t10 loc assign");
    long houseId = createHouse(token, plantId, "Home");
    long roomId = createRoom(token, houseId, "Kitchen");

    mockMvc
        .perform(
            assignRequest(plantId, roomId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andExpect(jsonPath("$.room.id").value(roomId))
        .andExpect(jsonPath("$.room.name").value("Kitchen"))
        .andExpect(jsonPath("$.room.houseId").value(houseId))
        .andExpect(jsonPath("$.house.id").value(houseId))
        .andExpect(jsonPath("$.house.name").value("Home"));
  }

  /** A plant can live in several rooms: each assignment succeeds and lists back. */
  @Test
  void testAssignLocation_AssignsMultipleLocations_WhenPlantHasSeveralRooms() throws Exception {
    String token = signupAndSignin("t10-loc-assign-multi");
    long plantId = registerPlant(token, "Ficus t10 loc multi", "Ficus t10 loc multi");
    long houseId = createHouse(token, plantId, "Home");
    long kitchenId = createRoom(token, houseId, "Kitchen");
    long balconyId = createRoom(token, houseId, "Balcony");

    mockMvc
        .perform(
            assignRequest(plantId, kitchenId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated());
    mockMvc
        .perform(
            assignRequest(plantId, balconyId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated());

    List<JsonNode> body = locationsBody(token, plantId);
    assertEquals(2, body.size(), "both locations must be listed");
  }

  /** Assigning a plant to a room it is already in is a 409. */
  @Test
  void testAssignLocation_Returns409_WhenPlantAlreadyAssignedToRoom() throws Exception {
    String token = signupAndSignin("t10-loc-assign-dup");
    long plantId = registerPlant(token, "Ficus t10 loc dup", "Ficus t10 loc dup");
    long houseId = createHouse(token, plantId, "Home");
    long roomId = createRoom(token, houseId, "Kitchen");
    mockMvc
        .perform(
            assignRequest(plantId, roomId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            assignRequest(plantId, roomId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isConflict());
  }

  /** Assigning an unknown room is a 404. */
  @Test
  void testAssignLocation_Returns404_WhenRoomNotFound() throws Exception {
    String token = signupAndSignin("t10-loc-assign-404");
    long plantId = registerPlant(token, "Ficus t10 loc assign 404", "Ficus t10 loc assign 404");

    mockMvc
        .perform(
            assignRequest(plantId, 9999999).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /** Assigning a room that lives under another user's house is a 404. */
  @Test
  void testAssignLocation_Returns404_WhenRoomOwnedByOtherUser() throws Exception {
    String ownerToken = signupAndSignin("t10-loc-assign-foreign-owner");
    long plantId =
        registerPlant(ownerToken, "Ficus t10 loc foreign assign", "Ficus t10 loc foreign assign");
    long houseId = createHouse(ownerToken, plantId, "Foreign home");
    long roomId = createRoom(ownerToken, houseId, "Foreign room");

    String strangerToken = signupAndSignin("t10-loc-assign-foreign-stranger");
    long strangerPlantId =
        registerPlant(strangerToken, "Ficus t10 loc foreign self", "Ficus t10 loc foreign self");

    mockMvc
        .perform(
            assignRequest(strangerPlantId, roomId)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + strangerToken))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /** Assigning a plant that does not exist is a 404. */
  @Test
  void testAssignLocation_Returns404_WhenPlantNotFound() throws Exception {
    String token = signupAndSignin("t10-loc-assign-plant404");
    long plantId = registerPlant(token, "Ficus t10 loc plant 404", "Ficus t10 loc plant 404");
    long houseId = createHouse(token, plantId, "Home");
    long roomId = createRoom(token, houseId, "Kitchen");

    mockMvc
        .perform(
            assignRequest(9999999, roomId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /** Listing a plant's locations returns each assignment with nested house and room. */
  @Test
  void testListLocations_ReturnsAssignments_WhenPlantHasLocations() throws Exception {
    String token = signupAndSignin("t10-loc-list-1");
    long plantId = registerPlant(token, "Ficus t10 loc list", "Ficus t10 loc list");
    long houseId = createHouse(token, plantId, "Home");
    long roomId = createRoom(token, houseId, "Kitchen");
    mockMvc
        .perform(
            assignRequest(plantId, roomId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated());

    List<JsonNode> body = locationsBody(token, plantId);
    assertEquals(1, body.size(), "exactly one location must be listed");
    JsonNode location = body.get(0);
    assertEquals(roomId, location.path("room").path("id").asLong());
    assertEquals("Kitchen", location.path("room").path("name").asText());
    assertEquals(houseId, location.path("room").path("houseId").asLong());
    assertEquals(houseId, location.path("house").path("id").asLong());
    assertEquals("Home", location.path("house").path("name").asText());
  }

  /** Listing a plant without locations returns an empty list, not an error. */
  @Test
  void testListLocations_ReturnsEmptyList_WhenPlantHasNoLocations() throws Exception {
    String token = signupAndSignin("t10-loc-list-empty");
    long plantId = registerPlant(token, "Ficus t10 loc list empty", "Ficus t10 loc list empty");
    long houseId = createHouse(token, plantId, "Home");
    long roomId = createRoom(token, houseId, "Kitchen");

    assertTrue(locationsBody(token, plantId).isEmpty(), "no location may be listed");
  }

  /**
   * Precondition: two users assigned the same catalog plant to rooms in their own houses, and the
   * first user assigned it twice. Each caller lists only their own locations: the first sees both
   * of theirs in assignment order, and neither sees the other's room or house ids.
   */
  @Test
  void testListLocations_ReturnsOnlyCallerOwnedLocations_WhenTwoUsersAssignedSamePlant()
      throws Exception {
    String firstToken = signupAndSignin("t32-loc-isolation-first");
    long plantId = registerPlant(firstToken, "Ficus t32 isolation", "Ficus t32 isolation");
    long firstHouseId = createHouse(firstToken, plantId, "First home");
    long firstKitchenId = createRoom(firstToken, firstHouseId, "First kitchen");
    long firstBalconyId = createRoom(firstToken, firstHouseId, "First balcony");
    assign(firstToken, plantId, firstKitchenId);
    assign(firstToken, plantId, firstBalconyId);

    String secondToken = signupAndSignin("t32-loc-isolation-second");
    long secondHouseId = createHouse(secondToken, plantId, "Second home");
    long secondRoomId = createRoom(secondToken, secondHouseId, "Second room");
    assign(secondToken, plantId, secondRoomId);

    List<JsonNode> firstBody = locationsBody(firstToken, plantId);
    assertEquals(
        List.of(firstKitchenId, firstBalconyId),
        firstBody.stream().map(location -> location.path("room").path("id").asLong()).toList(),
        "the first caller must see all of their own locations in assignment order");
    assertEquals(
        List.of(firstHouseId),
        firstBody.stream()
            .map(location -> location.path("house").path("id").asLong())
            .distinct()
            .toList(),
        "the first caller must only see their own house");

    List<JsonNode> secondBody = locationsBody(secondToken, plantId);
    assertEquals(
        List.of(secondRoomId),
        secondBody.stream().map(location -> location.path("room").path("id").asLong()).toList(),
        "the second caller must only see their own room");
    assertEquals(
        List.of(secondHouseId),
        secondBody.stream().map(location -> location.path("house").path("id").asLong()).toList(),
        "the second caller must only see their own house");
  }

  /**
   * Precondition: another user assigned the plant, and the caller has no location for it. The
   * caller gets an empty array, not the other user's location and not a 404.
   */
  @Test
  void testListLocations_ReturnsEmptyList_WhenOnlyOtherUserAssignedPlant() throws Exception {
    String ownerToken = signupAndSignin("t32-loc-isolation-owner");
    long plantId = registerPlant(ownerToken, "Ficus t32 isolated", "Ficus t32 isolated");
    long ownerHouseId = createHouse(ownerToken, plantId, "Owner home");
    long ownerRoomId = createRoom(ownerToken, ownerHouseId, "Owner room");
    assign(ownerToken, plantId, ownerRoomId);

    String strangerToken = signupAndSignin("t32-loc-isolation-stranger");

    assertTrue(
        locationsBody(strangerToken, plantId).isEmpty(),
        "another user's location may not be listed");
  }

  /** Listing locations for an unknown plant is a 404. */
  @Test
  void testListLocations_Returns404_WhenPlantNotFound() throws Exception {
    String token = signupAndSignin("t10-loc-list-404");

    mockMvc
        .perform(
            get("/api/v1/plants/9999999/locations")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /** Builds the house creation request. */
  private MockHttpServletRequestBuilder houseRequest(long plantId, String name) {
    return post("/api/v1/plants/" + plantId + "/locations/houses")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .content("{\"name\":\"" + name + "\"}");
  }

  /** Builds the room creation request. */
  private MockHttpServletRequestBuilder roomRequest(long houseId, String name) {
    return post("/api/v1/houses/" + houseId + "/rooms")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .content("{\"name\":\"" + name + "\"}");
  }

  /** Builds the location assignment request. */
  private MockHttpServletRequestBuilder assignRequest(long plantId, long roomId) {
    return post("/api/v1/plants/" + plantId + "/locations")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .content("{\"roomId\":" + roomId + "}");
  }

  /** Assigns the plant to the room through the REST endpoint and asserts the assignment. */
  private void assign(String token, long plantId, long roomId) throws Exception {
    mockMvc
        .perform(
            assignRequest(plantId, roomId).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated());
  }

  /** Runs the location list endpoint and parses the response body. */
  private List<JsonNode> locationsBody(String token, long plantId) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                get("/api/v1/plants/" + plantId + "/locations")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    return List.of(JSON.readValue(result.getResponse().getContentAsString(), JsonNode[].class));
  }

  /** Registers a plant through the REST endpoint and returns its id. */
  private long registerPlant(String token, String name, String scientificName) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/plants")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(
                        """
                        {"commonName":"%s","scientificName":"%s","waterFrequency":7}"""
                            .formatted(name, scientificName))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();
    return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  /** Creates a house through the REST endpoint and returns its id. */
  private long createHouse(String token, long plantId, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                houseRequest(plantId, name).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  /** Creates a room through the REST endpoint and returns its id. */
  private long createRoom(String token, long houseId, String name) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                roomRequest(houseId, name).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andReturn();
    return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
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
