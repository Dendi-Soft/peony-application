package com.dendi.peony.controller.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantLocation;
import com.dendi.peony.entity.Room;
import com.dendi.peony.entity.UserPlant;
import com.dendi.peony.repository.HouseRepository;
import com.dendi.peony.repository.PlantLocationRepository;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.repository.RoomRepository;
import com.dendi.peony.repository.UserPlantRepository;
import com.dendi.peony.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Tests for GET /api/v1/me/plants at the REST seam, against a real PostgreSQL database: only the
 * caller's user plants, newest first, tier-shaped plant fields, always-present nickname and
 * watering dates, and caller-owned locations.
 *
 * <p>Tests share one database, so each test uses its own account and unique plant names.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class MePlantsControllerTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** Fixed watering dates so assertions do not depend on the clock. */
  private static final LocalDateTime LAST_WATERED = LocalDateTime.of(2026, 1, 2, 8, 30, 15);

  private static final LocalDateTime NEXT_WATER_DUE = LocalDateTime.of(2026, 1, 9, 8, 30, 15);

  @Autowired private MockMvc mockMvc;

  @Autowired private PlantRepository plantRepository;

  @Autowired private UserPlantRepository userPlantRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private HouseRepository houseRepository;

  @Autowired private RoomRepository roomRepository;

  @Autowired private PlantLocationRepository plantLocationRepository;

  /** The endpoint is protected: no bearer token means the standard 401 envelope. */
  @Test
  void testListMyPlants_Returns401_WhenNoBearer() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/plants"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /** An authenticated caller with no user plants gets an empty list, not an error. */
  @Test
  void testListMyPlants_ReturnsEmptyList_WhenCallerHasNoPlants() throws Exception {
    String token = signupAndSignin("t25-my-plants-empty");

    assertTrue(myPlants(token).isEmpty(), "a caller with no plants must get an empty list");
  }

  /** The listing is scoped to the caller: other users' plants never appear. */
  @Test
  void testListMyPlants_ReturnsOnlyCallerPlants_WhenOtherUsersHavePlants() throws Exception {
    String token = signupAndSignin("t25-only-caller");
    String otherToken = signupAndSignin("t25-only-other");
    Plant first = seedPlant("Monstera", "Monstera t25 only first");
    Plant second = seedPlant("Snake plant", "Dracaena t25 only second");
    Plant foreign = seedPlant("Ficus", "Ficus t25 only foreign");
    linkPlant("t25-only-caller", first, "Mine one", LAST_WATERED, NEXT_WATER_DUE, 1);
    linkPlant("t25-only-caller", second, "Mine two", LAST_WATERED, NEXT_WATER_DUE, 2);
    linkPlant("t25-only-other", foreign, "Not mine", LAST_WATERED, NEXT_WATER_DUE, 3);

    assertEquals(
        Set.of(first.getId(), second.getId()),
        plantIds(myPlants(token)),
        "only the caller's plants may be listed");

    assertEquals(
        Set.of(foreign.getId()),
        plantIds(myPlants(otherToken)),
        "the other user must see only their own plant");
  }

  /** Rows are ordered by user-plant creation timestamp, newest first. */
  @Test
  void testListMyPlants_ReturnsNewestFirst_WhenCallerHasSeveralPlants() throws Exception {
    String token = signupAndSignin("t25-order-newest");
    Plant oldest = seedPlant("Monstera", "Monstera t25 order oldest");
    Plant newest = seedPlant("Snake plant", "Dracaena t25 order newest");
    Plant middle = seedPlant("Ficus", "Ficus t25 order middle");
    linkPlant("t25-order-newest", oldest, "oldest", LAST_WATERED, NEXT_WATER_DUE, 1);
    linkPlant("t25-order-newest", newest, "newest", LAST_WATERED, NEXT_WATER_DUE, 3);
    linkPlant("t25-order-newest", middle, "middle", LAST_WATERED, NEXT_WATER_DUE, 2);

    assertEquals(
        List.of(newest.getId(), middle.getId(), oldest.getId()),
        plantIdsInOrder(myPlants(token)),
        "rows must be newest first by user-plant creation timestamp");
  }

  /** Ties on the creation timestamp fall back to the user-plant id, descending. */
  @Test
  void testListMyPlants_OrdersByIdDescending_WhenCreatedAtTies() throws Exception {
    String token = signupAndSignin("t25-order-tie");
    Plant first = seedPlant("Monstera", "Monstera t25 tie first");
    Plant second = seedPlant("Snake plant", "Dracaena t25 tie second");
    linkPlant("t25-order-tie", first, "first", LAST_WATERED, NEXT_WATER_DUE, 1);
    linkPlant("t25-order-tie", second, "second", LAST_WATERED, NEXT_WATER_DUE, 1);

    assertEquals(
        List.of(second.getId(), first.getId()),
        plantIdsInOrder(myPlants(token)),
        "equal timestamps must order by user-plant id descending");
  }

  /** Free-tier rows omit the five premium-only plant fields (absent, not null). */
  @Test
  void testListMyPlants_OmitsPremiumFields_WhenCallerIsFreeTier() throws Exception {
    String token = signupAndSignin("t25-tier-free");
    Plant plant = seedFullPlant("Monstera", "Monstera t25 tier free");
    linkPlant("t25-tier-free", plant, "free plant", LAST_WATERED, NEXT_WATER_DUE, 1);

    JsonNode row = myPlants(token).get(0);
    assertEquals("Monstera", row.path("commonName").asText());
    assertEquals("Araceae", row.path("family").asText());
    assertEquals(3, row.path("tags").size());
    assertEquals("bright indirect", row.path("lightNeeds").asText());
    assertEquals(7, row.path("waterFrequency").asInt());
    assertTrue(row.path("toPets").asBoolean());
    assertTrue(row.has("toHumans"), "free-tier toxicity fields must be present");
    assertEquals(false, row.path("toHumans").asBoolean());
    assertFalse(row.has("humidity"), "premium-only fields must be absent, not null");
    assertFalse(row.has("tempRange"), "premium-only fields must be absent, not null");
    assertFalse(row.has("difficulty"), "premium-only fields must be absent, not null");
    assertFalse(row.has("nativeRegion"), "premium-only fields must be absent, not null");
    assertFalse(row.has("matureSize"), "premium-only fields must be absent, not null");
  }

  /** Premium-tier rows carry the full plant object. */
  @Test
  void testListMyPlants_ReturnsFullPlantFields_WhenCallerIsPremiumTier() throws Exception {
    String token = signupAndSignin("t25-tier-premium");
    upgrade(token);
    Plant plant = seedFullPlant("Monstera", "Monstera t25 tier premium");
    linkPlant("t25-tier-premium", plant, "premium plant", LAST_WATERED, NEXT_WATER_DUE, 1);

    JsonNode row = myPlants(token).get(0);
    assertEquals("high", row.path("humidity").asText());
    assertEquals("18-28C", row.path("tempRange").asText());
    assertEquals("easy", row.path("difficulty").asText());
    assertEquals("South America", row.path("nativeRegion").asText());
    assertEquals("3m tall", row.path("matureSize").asText());
  }

  /** Nickname and both watering dates are always present on every row. */
  @Test
  void testListMyPlants_ReturnsNicknameAndWateringDates_WhenCallerHasPlant() throws Exception {
    String token = signupAndSignin("t25-user-state");
    Plant plant = seedPlant("Monstera", "Monstera t25 user state");
    linkPlant("t25-user-state", plant, "Living room monstera", LAST_WATERED, NEXT_WATER_DUE, 1);

    JsonNode row = myPlants(token).get(0);
    assertEquals("Living room monstera", row.path("nickname").asText());
    assertEquals("2026-01-02T08:30:15", row.path("lastWateredAt").asText());
    assertEquals("2026-01-09T08:30:15", row.path("nextWaterDueAt").asText());
  }

  /** A plant with no caller-owned location carries an empty locations array. */
  @Test
  void testListMyPlants_ReturnsEmptyLocations_WhenPlantHasNoLocations() throws Exception {
    String token = signupAndSignin("t25-locations-empty");
    Plant plant = seedPlant("Monstera", "Monstera t25 locations empty");
    linkPlant("t25-locations-empty", plant, "no location", LAST_WATERED, NEXT_WATER_DUE, 1);

    JsonNode locations = myPlants(token).get(0).path("locations");
    assertTrue(locations.isArray(), "locations must always be an array");
    assertEquals(0, locations.size(), "a plant without locations must get an empty array");
  }

  /** A caller-owned location is embedded with the existing location response shape. */
  @Test
  void testListMyPlants_ReturnsCallerLocations_WhenPlantAssignedToCallerRoom() throws Exception {
    String token = signupAndSignin("t25-location-one");
    Plant plant = seedPlant("Monstera", "Monstera t25 location one");
    linkPlant("t25-location-one", plant, "window plant", LAST_WATERED, NEXT_WATER_DUE, 1);
    House house = seedHouse("t25-location-one", "Home");
    Room room = seedRoom(house, "Kitchen");
    PlantLocation location = seedLocation(plant, room);

    JsonNode locations = myPlants(token).get(0).path("locations");
    assertEquals(1, locations.size(), "the caller-owned location must be listed");
    JsonNode node = locations.get(0);
    assertEquals(location.getId(), node.path("id").asLong());
    assertEquals(room.getId(), node.path("room").path("id").asLong());
    assertEquals("Kitchen", node.path("room").path("name").asText());
    assertEquals(house.getId(), node.path("room").path("houseId").asLong());
    assertEquals(house.getId(), node.path("house").path("id").asLong());
    assertEquals("Home", node.path("house").path("name").asText());
  }

  /**
   * Locations never leak across users: when both users link the same catalog plant, each row embeds
   * only the rooms under its own caller's houses.
   */
  @Test
  void testListMyPlants_ReturnsOnlyCallerLocations_WhenBothUsersLinkedSamePlant() throws Exception {
    String aToken = signupAndSignin("t25-loc-shared-a");
    String bToken = signupAndSignin("t25-loc-shared-b");
    Plant plant = seedPlant("Monstera", "Monstera t25 loc shared");
    linkPlant("t25-loc-shared-a", plant, "A plant", LAST_WATERED, NEXT_WATER_DUE, 1);
    linkPlant("t25-loc-shared-b", plant, "B plant", LAST_WATERED, NEXT_WATER_DUE, 1);
    House aHouse = seedHouse("t25-loc-shared-a", "A home");
    Room aRoom = seedRoom(aHouse, "A kitchen");
    House bHouse = seedHouse("t25-loc-shared-b", "B home");
    Room bRoom = seedRoom(bHouse, "B balcony");
    seedLocation(plant, aRoom);
    seedLocation(plant, bRoom);

    JsonNode aLocations = myPlants(aToken).get(0).path("locations");
    assertEquals(1, aLocations.size(), "only the caller's own location may be listed");
    assertEquals(aRoom.getId(), aLocations.get(0).path("room").path("id").asLong());
    assertEquals("A kitchen", aLocations.get(0).path("room").path("name").asText());
    assertEquals(aHouse.getId(), aLocations.get(0).path("house").path("id").asLong());

    JsonNode bLocations = myPlants(bToken).get(0).path("locations");
    assertEquals(1, bLocations.size(), "only the caller's own location may be listed");
    assertEquals(bRoom.getId(), bLocations.get(0).path("room").path("id").asLong());
    assertEquals("B balcony", bLocations.get(0).path("room").path("name").asText());
    assertEquals(bHouse.getId(), bLocations.get(0).path("house").path("id").asLong());
  }

  /** Runs the my-plants endpoint and parses the response body. */
  private List<JsonNode> myPlants(String token) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/me/plants").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    return List.of(JSON.readValue(result.getResponse().getContentAsString(), JsonNode[].class));
  }

  private static Set<Long> plantIds(List<JsonNode> body) {
    return body.stream().map(node -> node.path("id").asLong()).collect(Collectors.toSet());
  }

  private static List<Long> plantIdsInOrder(List<JsonNode> body) {
    return body.stream().map(node -> node.path("id").asLong()).toList();
  }

  /** Seeds a catalog plant so tier visibility and user state can be asserted. */
  private Plant seedPlant(String name, String scientificName) {
    Plant plant = new Plant();
    plant.setName(name);
    plant.setScientificName(scientificName);
    plant.setDescription("seeded for testing");
    plant.setWateringFrequency(7);
    plant.setCreatedAt(LocalDateTime.now());
    return plantRepository.save(plant);
  }

  /** Seeds a fully populated catalog plant, including the premium-only fields. */
  private Plant seedFullPlant(String name, String scientificName) {
    Plant plant = seedPlant(name, scientificName);
    plant.setFamily("Araceae");
    plant.setTags(new String[] {"tropical", "low light", "pet friendly"});
    plant.setLightNeeds("bright indirect");
    plant.setHumidity("high");
    plant.setTempRange("18-28C");
    plant.setToPets(true);
    plant.setToHumans(false);
    plant.setDifficulty("easy");
    plant.setNativeRegion("South America");
    plant.setMatureSize("3m tall");
    return plantRepository.save(plant);
  }

  /** Links a seeded plant to the user at a fixed creation minute for ordering assertions. */
  private UserPlant linkPlant(
      String username,
      Plant plant,
      String nickname,
      LocalDateTime lastWateredAt,
      LocalDateTime nextWaterDueAt,
      int createdMinute) {
    LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, createdMinute);
    UserPlant link = new UserPlant();
    link.setUserId(userId(username));
    link.setPlantId(plant.getId());
    link.setNickname(nickname);
    link.setLastWateredAt(lastWateredAt);
    link.setNextWaterDueAt(nextWaterDueAt);
    link.setCreatedAt(createdAt);
    link.setUpdatedAt(createdAt);
    return userPlantRepository.save(link);
  }

  private Long userId(String username) {
    return userRepository.findByUsername(username).orElseThrow().getId();
  }

  /** Seeds a house owned by the user. */
  private House seedHouse(String username, String name) {
    House house = new House();
    house.setUserId(userId(username));
    house.setName(name);
    house.setCreatedAt(LocalDateTime.of(2026, 1, 1, 13, 0));
    return houseRepository.save(house);
  }

  /** Seeds a room under a house. */
  private Room seedRoom(House house, String name) {
    Room room = new Room();
    room.setHouseId(house.getId());
    room.setName(name);
    room.setCreatedAt(LocalDateTime.of(2026, 1, 1, 13, 1));
    return roomRepository.save(room);
  }

  /** Seeds a plant-to-room assignment. */
  private PlantLocation seedLocation(Plant plant, Room room) {
    PlantLocation location = new PlantLocation();
    location.setPlantId(plant.getId());
    location.setRoomId(room.getId());
    location.setCreatedAt(LocalDateTime.of(2026, 1, 1, 13, 2));
    return plantLocationRepository.save(location);
  }

  private void upgrade(String token) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/subscription/upgrade")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk());
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
