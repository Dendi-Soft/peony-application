package com.dendi.peony.controller.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.Room;
import com.dendi.peony.entity.UserPlant;
import com.dendi.peony.entity.UserPlantImage;
import com.dendi.peony.repository.HouseRepository;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.repository.RoomRepository;
import com.dendi.peony.repository.UserPlantImageRepository;
import com.dendi.peony.repository.UserPlantRepository;
import com.dendi.peony.repository.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Tests for the /api/v1/me/plants endpoints at the REST seam, against a real PostgreSQL database:
 * caller-scoped listing and detail, create/update/delete, watering, the individual-shaped location,
 * and tier-shaped plant fields.
 *
 * <p>The clock is fixed so watering assertions are deterministic. Tests share one database, so each
 * test uses its own account and unique plant names.
 */
@Import({TestcontainersConfiguration.class, MePlantsControllerTest.FixedClockConfig.class})
@SpringBootTest
@AutoConfigureMockMvc
class MePlantsControllerTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  /** Fixed "now" for create/update/water so expected timestamps are literals. */
  private static final String NOW = "2026-06-01T10:00:00";

  private static final LocalDateTime SEEDED_LAST_WATERED = LocalDateTime.of(2026, 1, 2, 8, 30, 15);

  private static final LocalDateTime SEEDED_NEXT_DUE = LocalDateTime.of(2026, 1, 9, 8, 30, 15);

  /** Replaces the application clock with a fixed instant for deterministic watering dates. */
  @TestConfiguration
  static class FixedClockConfig {

    @Bean
    @Primary
    Clock fixedClock() {
      return Clock.fixed(Instant.parse("2026-06-01T10:00:00Z"), ZoneOffset.UTC);
    }
  }

  @Autowired private MockMvc mockMvc;

  @Autowired private PlantRepository plantRepository;

  @Autowired private UserPlantRepository userPlantRepository;

  @Autowired private UserPlantImageRepository userPlantImageRepository;

  @Autowired private UserRepository userRepository;

  @Autowired private HouseRepository houseRepository;

  @Autowired private RoomRepository roomRepository;

  /** The endpoint is protected: no bearer token means the standard 401 envelope. */
  @Test
  void testListMyPlants_Returns401_WhenNoBearer() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/plants"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /** An authenticated caller with no individuals gets an empty list, not an error. */
  @Test
  void testListMyPlants_ReturnsEmptyList_WhenCallerHasNoPlants() throws Exception {
    String token = signupAndSignin("t37-my-plants-empty");

    assertTrue(myPlants(token).isEmpty(), "a caller with no individuals must get an empty list");
  }

  /** The listing is scoped to the caller: other users' individuals never appear. */
  @Test
  void testListMyPlants_ReturnsOnlyCallerIndividuals_WhenOtherUsersHavePlants() throws Exception {
    String token = signupAndSignin("t37-only-caller");
    String otherToken = signupAndSignin("t37-only-other");
    Plant first = seedPlant("Monstera", "Monstera t37 only first", 7);
    Plant second = seedPlant("Snake plant", "Dracaena t37 only second", 10);
    Plant foreign = seedPlant("Ficus", "Ficus t37 only foreign", 7);
    UserPlant mineOne = seedIndividual("t37-only-caller", first, "Mine one", 1);
    UserPlant mineTwo = seedIndividual("t37-only-caller", second, "Mine two", 2);
    UserPlant foreignIndividual = seedIndividual("t37-only-other", foreign, "Not mine", 3);

    assertEquals(
        Set.of(mineOne.getId(), mineTwo.getId()),
        ids(myPlants(token)),
        "only the caller's individuals may be listed");

    assertEquals(
        Set.of(foreignIndividual.getId()),
        ids(myPlants(otherToken)),
        "the other user must see only their own individual");
  }

  /** Rows are ordered by individual creation timestamp, newest first. */
  @Test
  void testListMyPlants_ReturnsNewestFirst_WhenCallerHasSeveralIndividuals() throws Exception {
    String token = signupAndSignin("t37-order-newest");
    UserPlant oldest =
        seedIndividual("t37-order-newest", seedPlant("M", "M t37 order old", 7), "o", 1);
    UserPlant newest =
        seedIndividual("t37-order-newest", seedPlant("S", "S t37 order new", 7), "n", 3);
    UserPlant middle =
        seedIndividual("t37-order-newest", seedPlant("F", "F t37 order mid", 7), "m", 2);

    assertEquals(
        List.of(newest.getId(), middle.getId(), oldest.getId()),
        idsInOrder(myPlants(token)),
        "individuals must be newest first by creation timestamp");
  }

  /** Ties on the creation timestamp fall back to the individual id, descending. */
  @Test
  void testListMyPlants_OrdersByIdDescending_WhenCreatedAtTies() throws Exception {
    String token = signupAndSignin("t37-order-tie");
    UserPlant first =
        seedIndividual("t37-order-tie", seedPlant("M", "M t37 tie first", 7), "first", 1);
    UserPlant second =
        seedIndividual("t37-order-tie", seedPlant("S", "S t37 tie second", 7), "second", 1);

    assertEquals(
        List.of(second.getId(), first.getId()),
        idsInOrder(myPlants(token)),
        "equal timestamps must order by individual id descending");
  }

  /** Free-tier rows omit the five premium-only plant fields (absent, not null). */
  @Test
  void testListMyPlants_OmitsPremiumFields_WhenCallerIsFreeTier() throws Exception {
    String token = signupAndSignin("t37-tier-free");
    Plant plant = seedFullPlant("Monstera", "Monstera t37 tier free");
    seedIndividual("t37-tier-free", plant, "free plant", 1);

    JsonNode row = myPlants(token).get(0);
    assertEquals("Monstera", row.path("commonName").asText());
    assertEquals("Araceae", row.path("family").asText());
    assertEquals(3, row.path("tags").size());
    assertEquals("bright indirect", row.path("lightNeeds").asText());
    assertEquals(7, row.path("waterFrequency").asInt());
    assertTrue(row.path("toPets").asBoolean());
    assertTrue(row.has("toHumans"), "free-tier toxicity fields must be present");
    assertFalse(row.has("humidity"), "premium-only fields must be absent, not null");
    assertFalse(row.has("tempRange"), "premium-only fields must be absent, not null");
    assertFalse(row.has("difficulty"), "premium-only fields must be absent, not null");
    assertFalse(row.has("nativeRegion"), "premium-only fields must be absent, not null");
    assertFalse(row.has("matureSize"), "premium-only fields must be absent, not null");
  }

  /** Premium-tier rows carry the full plant object. */
  @Test
  void testListMyPlants_ReturnsFullPlantFields_WhenCallerIsPremiumTier() throws Exception {
    String token = signupAndSignin("t37-tier-premium");
    upgrade(token);
    Plant plant = seedFullPlant("Monstera", "Monstera t37 tier premium");
    seedIndividual("t37-tier-premium", plant, "premium plant", 1);

    JsonNode row = myPlants(token).get(0);
    assertEquals("high", row.path("humidity").asText());
    assertEquals("18-28C", row.path("tempRange").asText());
    assertEquals("easy", row.path("difficulty").asText());
    assertEquals("South America", row.path("nativeRegion").asText());
    assertEquals("3m tall", row.path("matureSize").asText());
  }

  /** The individual's own id, plant id, nickname and watering dates are returned. */
  @Test
  void testListMyPlants_ReturnsIndividualFields_WhenCallerHasPlant() throws Exception {
    String token = signupAndSignin("t37-user-state");
    Plant plant = seedPlant("Monstera", "Monstera t37 user state", 7);
    UserPlant individual = seedIndividual("t37-user-state", plant, "Living room monstera", 1);

    JsonNode row = myPlants(token).get(0);
    assertEquals(individual.getId(), row.path("id").asLong());
    assertEquals(plant.getId(), row.path("plantId").asLong());
    assertEquals("Living room monstera", row.path("nickname").asText());
    assertEquals("2026-01-02T08:30:15", row.path("lastWateredAt").asText());
    assertEquals("2026-01-09T08:30:15", row.path("nextWaterDueAt").asText());
    assertFalse(row.has("house"), "an individual without a room must not embed a house");
    assertFalse(row.has("room"), "an individual without a room must not embed a room");
  }

  /** Watering dates that are null are omitted (the models serialize absent, never null). */
  @Test
  void testListMyPlants_OmitsWateringDates_WhenIndividualNeverWatered() throws Exception {
    String token = signupAndSignin("t37-no-dates");
    UserPlant individual =
        seedIndividual("t37-no-dates", seedPlant("M", "M t37 no dates", 7), "", 1);
    individual.setLastWateredAt(null);
    individual.setNextWaterDueAt(null);
    userPlantRepository.save(individual);

    JsonNode row = myPlants(token).get(0);
    assertFalse(row.has("lastWateredAt"), "a null last-watered date must be absent");
    assertFalse(row.has("nextWaterDueAt"), "a null next-due date must be absent");
  }

  /** An individual without a personal photo reports hasImage=false and omits imageId. */
  @Test
  void testListMyPlants_OmitsImageIdAndReportsNoImage_WhenIndividualHasNoImage() throws Exception {
    String token = signupAndSignin("t44-list-no-image");
    seedIndividual("t44-list-no-image", seedPlant("M", "M t44 list no image", 7), "", 1);

    JsonNode row = myPlants(token).get(0);
    assertFalse(row.path("hasImage").asBoolean(), "an individual without a photo reports false");
    assertTrue(row.has("hasImage"), "hasImage is required and always present");
    assertFalse(row.has("imageId"), "imageId must be absent when there is no photo");
  }

  /** An individual with a personal photo reports hasImage=true and its immutable imageId. */
  @Test
  void testListMyPlants_ReturnsImageFields_WhenIndividualHasImage() throws Exception {
    String token = signupAndSignin("t44-list-image");
    UserPlant individual =
        seedIndividual("t44-list-image", seedPlant("M", "M t44 list image", 7), "", 1);
    UUID imageId = UUID.fromString("6d5f4e3c-2b1a-4f0e-9d8c-7b6a5f4e3d2c");
    seedImage(individual, imageId);

    JsonNode row = myPlants(token).get(0);
    assertTrue(row.path("hasImage").asBoolean(), "an individual with a photo reports true");
    assertEquals(imageId.toString(), row.path("imageId").asText());
  }

  /** The detail route carries the same image fields as the listing row. */
  @Test
  void testGetMyPlant_ReturnsImageFields_WhenIndividualHasImage() throws Exception {
    String token = signupAndSignin("t44-get-image");
    UserPlant individual =
        seedIndividual("t44-get-image", seedPlant("M", "M t44 get image", 7), "", 1);
    UUID imageId = UUID.fromString("1a2b3c4d-5e6f-4a1b-8c9d-0e1f2a3b4c5d");
    seedImage(individual, imageId);

    mockMvc
        .perform(
            get("/api/v1/me/plants/" + individual.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.hasImage").value(true))
        .andExpect(jsonPath("$.imageId").value(imageId.toString()));
  }

  /** A caller-owned room embeds both the room and its house, in the location response shape. */
  @Test
  void testListMyPlants_ReturnsRoomAndHouse_WhenIndividualIsPlaced() throws Exception {
    String token = signupAndSignin("t37-location");
    House house = seedHouse("t37-location", "Home");
    Room room = seedRoom(house, "Kitchen");
    Plant plant = seedPlant("Monstera", "Monstera t37 location", 7);
    UserPlant individual = seedIndividual("t37-location", plant, "window plant", 1);
    individual.setRoomId(room.getId());
    userPlantRepository.save(individual);

    JsonNode row = myPlants(token).get(0);
    assertEquals(room.getId(), row.path("room").path("id").asLong());
    assertEquals("Kitchen", row.path("room").path("name").asText());
    assertEquals(house.getId(), row.path("room").path("houseId").asLong());
    assertEquals(house.getId(), row.path("house").path("id").asLong());
    assertEquals("Home", row.path("house").path("name").asText());
  }

  /**
   * A room that resolves outside the caller's houses is hidden: both the room and house are absent
   * rather than leaking another user's location.
   */
  @Test
  void testListMyPlants_HidesRoomAndHouse_WhenRoomBelongsToAnotherUser() throws Exception {
    String token = signupAndSignin("t37-foreign-location-a");
    signupAndSignin("t37-foreign-location-b");
    House otherHouse = seedHouse("t37-foreign-location-b", "Other home");
    Room otherRoom = seedRoom(otherHouse, "Other kitchen");
    Plant plant = seedPlant("Monstera", "Monstera t37 foreign location", 7);
    UserPlant individual = seedIndividual("t37-foreign-location-a", plant, "leaky", 1);
    individual.setRoomId(otherRoom.getId());
    userPlantRepository.save(individual);

    JsonNode row = myPlants(token).get(0);
    assertFalse(row.has("room"), "another user's room must never be embedded");
    assertFalse(row.has("house"), "another user's house must never be embedded");
  }

  /** POST creates an individual with default nickname, no dates and no location. */
  @Test
  void testCreateMyPlant_CreatesIndividual_WhenMinimalRequest() throws Exception {
    String token = signupAndSignin("t37-create-min");
    Plant plant = seedPlant("Monstera", "Monstera t37 create min", 7);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/me/plants")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content("{\"plantId\":" + plant.getId() + "}")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.plantId").value(plant.getId()))
            .andExpect(jsonPath("$.nickname").value(""))
            .andExpect(jsonPath("$.lastWateredAt").doesNotExist())
            .andExpect(jsonPath("$.nextWaterDueAt").doesNotExist())
            .andExpect(jsonPath("$.house").doesNotExist())
            .andExpect(jsonPath("$.room").doesNotExist())
            .andReturn();

    long id = JSON.readTree(result.getResponse().getContentAsString()).path("id").asLong();
    assertEquals(
        id,
        userPlantRepository.findById(id).orElseThrow().getId(),
        "the response id must be the individual id, not the catalog plant id");
  }

  /** POST stores the nickname, room and last-watered date, and derives the next due date. */
  @Test
  void testCreateMyPlant_ReturnsPlacedWateredIndividual_WhenFullRequest() throws Exception {
    String token = signupAndSignin("t37-create-full");
    House house = seedHouse("t37-create-full", "Home");
    Room room = seedRoom(house, "Living room");
    Plant plant = seedPlant("Monstera", "Monstera t37 create full", 7);

    mockMvc
        .perform(
            post("/api/v1/me/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    createPayload(
                        plant.getId(), "Big Monstera", room.getId(), "2026-05-01T09:00:00"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nickname").value("Big Monstera"))
        .andExpect(jsonPath("$.lastWateredAt").value("2026-05-01T09:00:00"))
        .andExpect(jsonPath("$.nextWaterDueAt").value("2026-05-08T09:00:00"))
        .andExpect(jsonPath("$.room.id").value(room.getId()))
        .andExpect(jsonPath("$.house.id").value(house.getId()));
  }

  /** POST with an unknown catalog plant is a 404. */
  @Test
  void testCreateMyPlant_Returns404_WhenPlantNotFound() throws Exception {
    String token = signupAndSignin("t37-create-plant-404");

    mockMvc
        .perform(
            post("/api/v1/me/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"plantId\":9999999}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Plant not found"));
  }

  /** POST with another user's room is a 404 and creates nothing. */
  @Test
  void testCreateMyPlant_Returns404_WhenRoomBelongsToAnotherUser() throws Exception {
    String token = signupAndSignin("t37-create-room-a");
    signupAndSignin("t37-create-room-b");
    House otherHouse = seedHouse("t37-create-room-b", "Other home");
    Room otherRoom = seedRoom(otherHouse, "Other kitchen");
    Plant plant = seedPlant("Monstera", "Monstera t37 create room", 7);
    long before = userPlantRepository.count();

    mockMvc
        .perform(
            post("/api/v1/me/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"plantId\":" + plant.getId() + ",\"roomId\":" + otherRoom.getId() + "}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Room not found"));

    assertEquals(before, userPlantRepository.count(), "no individual may be created");
  }

  /** POST without a plant id is a 400. */
  @Test
  void testCreateMyPlant_Returns400_WhenPlantIdMissing() throws Exception {
    String token = signupAndSignin("t37-create-400");

    mockMvc
        .perform(
            post("/api/v1/me/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"nickname\":\"nameless\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  /** POST with a malformed last-watered date is a 400. */
  @Test
  void testCreateMyPlant_Returns400_WhenDateMalformed() throws Exception {
    String token = signupAndSignin("t37-create-date-400");
    Plant plant = seedPlant("Monstera", "Monstera t37 create date", 7);

    mockMvc
        .perform(
            post("/api/v1/me/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"plantId\":" + plant.getId() + ",\"lastWateredAt\":\"not-a-date\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  /** POST rejects a nickname longer than the 255-character column. */
  @Test
  void testCreateMyPlant_Returns400_WhenNicknameTooLong() throws Exception {
    String token = signupAndSignin("t37-create-nickname-long");
    Plant plant = seedPlant("Monstera", "Monstera t37 create nickname long", 7);

    mockMvc
        .perform(
            post("/api/v1/me/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    "{\"plantId\":" + plant.getId() + ",\"nickname\":\"" + "x".repeat(256) + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  /** POST accepts a nickname of exactly the 255-character column length. */
  @Test
  void testCreateMyPlant_AcceptsNicknameAtLimit_WhenNicknameHas255Characters() throws Exception {
    String token = signupAndSignin("t37-create-nickname-limit");
    Plant plant = seedPlant("Monstera", "Monstera t37 create nickname limit", 7);
    String nickname = "x".repeat(255);

    mockMvc
        .perform(
            post("/api/v1/me/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"plantId\":" + plant.getId() + ",\"nickname\":\"" + nickname + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nickname").value(nickname));
  }

  /** GET returns one caller-owned individual. */
  @Test
  void testGetMyPlant_ReturnsIndividual_WhenCallerOwnsIt() throws Exception {
    String token = signupAndSignin("t37-get-own");
    Plant plant = seedPlant("Monstera", "Monstera t37 get own", 7);
    UserPlant individual = seedIndividual("t37-get-own", plant, "mine", 1);

    mockMvc
        .perform(
            get("/api/v1/me/plants/" + individual.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(individual.getId()))
        .andExpect(jsonPath("$.plantId").value(plant.getId()))
        .andExpect(jsonPath("$.nickname").value("mine"));
  }

  /** GET of another user's individual is a 404, not a 403. */
  @Test
  void testGetMyPlant_Returns404_WhenIndividualBelongsToAnotherUser() throws Exception {
    String token = signupAndSignin("t37-get-other-a");
    signupAndSignin("t37-get-other-b");
    Plant plant = seedPlant("Monstera", "Monstera t37 get other", 7);
    UserPlant foreign = seedIndividual("t37-get-other-b", plant, "not yours", 1);

    mockMvc
        .perform(
            get("/api/v1/me/plants/" + foreign.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("User plant not found"));
  }

  /** PATCH replaces the editable state and recomputes the next due date. */
  @Test
  void testUpdateMyPlant_ReplacesState_WhenValidRequest() throws Exception {
    String token = signupAndSignin("t37-patch");
    House house = seedHouse("t37-patch", "Home");
    Room room = seedRoom(house, "Bathroom");
    Plant plant = seedPlant("Monstera", "Monstera t37 patch", 10);
    UserPlant individual = seedIndividual("t37-patch", plant, "old name", 1);

    mockMvc
        .perform(
            patch("/api/v1/me/plants/" + individual.getId())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(updatePayload("new name", room.getId(), "2026-05-30T08:00:00"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(individual.getId()))
        .andExpect(jsonPath("$.nickname").value("new name"))
        .andExpect(jsonPath("$.lastWateredAt").value("2026-05-30T08:00:00"))
        .andExpect(jsonPath("$.nextWaterDueAt").value("2026-06-09T08:00:00"))
        .andExpect(jsonPath("$.room.id").value(room.getId()))
        .andExpect(jsonPath("$.house.id").value(house.getId()));
  }

  /** PATCH is a replace: omitting the room clears the location. */
  @Test
  void testUpdateMyPlant_ClearsLocation_WhenRoomOmitted() throws Exception {
    String token = signupAndSignin("t37-patch-clear-room");
    House house = seedHouse("t37-patch-clear-room", "Home");
    Room room = seedRoom(house, "Kitchen");
    Plant plant = seedPlant("Monstera", "Monstera t37 patch clear room", 7);
    UserPlant individual = seedIndividual("t37-patch-clear-room", plant, "placed", 1);
    individual.setRoomId(room.getId());
    userPlantRepository.save(individual);

    mockMvc
        .perform(
            patch("/api/v1/me/plants/" + individual.getId())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"nickname\":\"placed\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.room").doesNotExist())
        .andExpect(jsonPath("$.house").doesNotExist());
  }

  /** PATCH is a replace: omitting the watering date clears the schedule. */
  @Test
  void testUpdateMyPlant_ClearsWateringSchedule_WhenDateOmitted() throws Exception {
    String token = signupAndSignin("t37-patch-clear-date");
    Plant plant = seedPlant("Monstera", "Monstera t37 patch clear date", 7);
    UserPlant individual = seedIndividual("t37-patch-clear-date", plant, "watered", 1);

    mockMvc
        .perform(
            patch("/api/v1/me/plants/" + individual.getId())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"nickname\":\"watered\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.lastWateredAt").doesNotExist())
        .andExpect(jsonPath("$.nextWaterDueAt").doesNotExist());
  }

  /** PATCH of another user's individual is a 404 and leaves it untouched. */
  @Test
  void testUpdateMyPlant_Returns404_WhenIndividualBelongsToAnotherUser() throws Exception {
    String token = signupAndSignin("t37-patch-other-a");
    signupAndSignin("t37-patch-other-b");
    Plant plant = seedPlant("Monstera", "Monstera t37 patch other", 7);
    UserPlant foreign = seedIndividual("t37-patch-other-b", plant, "untouched", 1);

    mockMvc
        .perform(
            patch("/api/v1/me/plants/" + foreign.getId())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"nickname\":\"hijacked\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound());

    assertEquals(
        "untouched",
        userPlantRepository.findById(foreign.getId()).orElseThrow().getNickname(),
        "another user's individual must not change");
  }

  /** PATCH with another user's room is a 404. */
  @Test
  void testUpdateMyPlant_Returns404_WhenRoomBelongsToAnotherUser() throws Exception {
    String token = signupAndSignin("t37-patch-room-a");
    signupAndSignin("t37-patch-room-b");
    House otherHouse = seedHouse("t37-patch-room-b", "Other home");
    Room otherRoom = seedRoom(otherHouse, "Other room");
    Plant plant = seedPlant("Monstera", "Monstera t37 patch room", 7);
    UserPlant individual = seedIndividual("t37-patch-room-a", plant, "mine", 1);

    mockMvc
        .perform(
            patch("/api/v1/me/plants/" + individual.getId())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"roomId\":" + otherRoom.getId() + "}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Room not found"));
  }

  /** PATCH with a malformed date is a 400. */
  @Test
  void testUpdateMyPlant_Returns400_WhenDateMalformed() throws Exception {
    String token = signupAndSignin("t37-patch-date-400");
    Plant plant = seedPlant("Monstera", "Monstera t37 patch date", 7);
    UserPlant individual = seedIndividual("t37-patch-date-400", plant, "mine", 1);

    mockMvc
        .perform(
            patch("/api/v1/me/plants/" + individual.getId())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"lastWateredAt\":\"yesterday\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest());
  }

  /** PATCH rejects a nickname longer than the 255-character column. */
  @Test
  void testUpdateMyPlant_Returns400_WhenNicknameTooLong() throws Exception {
    String token = signupAndSignin("t37-patch-nickname-long");
    Plant plant = seedPlant("Monstera", "Monstera t37 patch nickname long", 7);
    UserPlant individual = seedIndividual("t37-patch-nickname-long", plant, "mine", 1);

    mockMvc
        .perform(
            patch("/api/v1/me/plants/" + individual.getId())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"nickname\":\"" + "x".repeat(256) + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  /** PATCH accepts a nickname of exactly the 255-character column length. */
  @Test
  void testUpdateMyPlant_AcceptsNicknameAtLimit_WhenNicknameHas255Characters() throws Exception {
    String token = signupAndSignin("t37-patch-nickname-limit");
    Plant plant = seedPlant("Monstera", "Monstera t37 patch nickname limit", 7);
    UserPlant individual = seedIndividual("t37-patch-nickname-limit", plant, "mine", 1);
    String nickname = "x".repeat(255);

    mockMvc
        .perform(
            patch("/api/v1/me/plants/" + individual.getId())
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"nickname\":\"" + nickname + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nickname").value(nickname));
  }

  /** DELETE removes only the individual; the catalog plant and room stay. */
  @Test
  void testDeleteMyPlant_DeletesOnlyIndividual_WhenCallerOwnsIt() throws Exception {
    String token = signupAndSignin("t37-delete-own");
    House house = seedHouse("t37-delete-own", "Home");
    Room room = seedRoom(house, "Kitchen");
    Plant plant = seedPlant("Monstera", "Monstera t37 delete own", 7);
    UserPlant individual = seedIndividual("t37-delete-own", plant, "gone", 1);
    individual.setRoomId(room.getId());
    userPlantRepository.save(individual);

    mockMvc
        .perform(
            delete("/api/v1/me/plants/" + individual.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNoContent());

    assertTrue(
        userPlantRepository.findById(individual.getId()).isEmpty(), "the individual must go");
    assertTrue(plantRepository.findById(plant.getId()).isPresent(), "the catalog plant must stay");
    assertTrue(roomRepository.findById(room.getId()).isPresent(), "the room must stay");
  }

  /** DELETE of another user's individual is a 404 and leaves it in place. */
  @Test
  void testDeleteMyPlant_Returns404_WhenIndividualBelongsToAnotherUser() throws Exception {
    String token = signupAndSignin("t37-delete-other-a");
    signupAndSignin("t37-delete-other-b");
    Plant plant = seedPlant("Monstera", "Monstera t37 delete other", 7);
    UserPlant foreign = seedIndividual("t37-delete-other-b", plant, "safe", 1);

    mockMvc
        .perform(
            delete("/api/v1/me/plants/" + foreign.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("User plant not found"));

    assertTrue(
        userPlantRepository.findById(foreign.getId()).isPresent(),
        "another user's individual must survive");
  }

  /** POST /water records the fixed now and recomputes the next due date from the frequency. */
  @Test
  void testWaterMyPlant_RecordsNowAndRecomputesDue_WhenCallerOwnsIt() throws Exception {
    String token = signupAndSignin("t37-water");
    Plant plant = seedPlant("Monstera", "Monstera t37 water", 7);
    UserPlant individual = seedIndividual("t37-water", plant, "thirsty", 1);
    individual.setLastWateredAt(LocalDateTime.of(2026, 1, 1, 9, 0));
    individual.setNextWaterDueAt(LocalDateTime.of(2026, 1, 8, 9, 0));
    userPlantRepository.save(individual);

    mockMvc
        .perform(
            post("/api/v1/me/plants/" + individual.getId() + "/water")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(individual.getId()))
        .andExpect(jsonPath("$.lastWateredAt").value(NOW))
        .andExpect(jsonPath("$.nextWaterDueAt").value("2026-06-08T10:00:00"));
  }

  /** POST /water on another user's individual is a 404. */
  @Test
  void testWaterMyPlant_Returns404_WhenIndividualBelongsToAnotherUser() throws Exception {
    String token = signupAndSignin("t37-water-other-a");
    signupAndSignin("t37-water-other-b");
    Plant plant = seedPlant("Monstera", "Monstera t37 water other", 7);
    UserPlant foreign = seedIndividual("t37-water-other-b", plant, "not yours", 1);

    mockMvc
        .perform(
            post("/api/v1/me/plants/" + foreign.getId() + "/water")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("User plant not found"));
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

  private static Set<Long> ids(List<JsonNode> body) {
    return body.stream().map(node -> node.path("id").asLong()).collect(Collectors.toSet());
  }

  private static List<Long> idsInOrder(List<JsonNode> body) {
    return body.stream().map(node -> node.path("id").asLong()).toList();
  }

  /** Seeds a catalog plant with a watering frequency. */
  private Plant seedPlant(String name, String scientificName, int waterFrequency) {
    Plant plant = new Plant();
    plant.setName(name);
    plant.setScientificName(scientificName);
    plant.setDescription("seeded for testing");
    plant.setWateringFrequency(waterFrequency);
    plant.setCreatedAt(LocalDateTime.now());
    return plantRepository.save(plant);
  }

  /** Seeds a fully populated catalog plant, including the premium-only fields. */
  private Plant seedFullPlant(String name, String scientificName) {
    Plant plant = seedPlant(name, scientificName, 7);
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

  /** Seeds an individual at a fixed creation minute for ordering assertions. */
  private UserPlant seedIndividual(
      String username, Plant plant, String nickname, int createdMinute) {
    LocalDateTime createdAt = LocalDateTime.of(2026, 1, 1, 12, createdMinute);
    UserPlant individual = new UserPlant();
    individual.setUserId(userId(username));
    individual.setPlantId(plant.getId());
    individual.setNickname(nickname);
    individual.setLastWateredAt(SEEDED_LAST_WATERED);
    individual.setNextWaterDueAt(SEEDED_NEXT_DUE);
    individual.setCreatedAt(createdAt);
    individual.setUpdatedAt(createdAt);
    return userPlantRepository.save(individual);
  }

  private Long userId(String username) {
    return userRepository.findByUsername(username).orElseThrow().getId();
  }

  /** Seeds an image row for an individual; the files are not needed for response-field tests. */
  private void seedImage(UserPlant individual, UUID imageId) {
    UserPlantImage image = new UserPlantImage();
    image.setUserPlantId(individual.getId());
    image.setImageId(imageId);
    image.setFilename(imageId + ".jpg");
    image.setThumbFilename(imageId + "-thumb.jpg");
    image.setWidth(1280);
    image.setHeight(960);
    image.setByteSize(123456L);
    image.setCreatedAt(LocalDateTime.of(2026, 1, 1, 12, 0));
    userPlantImageRepository.save(image);
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

  private static String createPayload(
      Long plantId, String nickname, Long roomId, String lastWateredAt) {
    return """
        {"plantId":%d,"nickname":"%s","roomId":%d,"lastWateredAt":"%s"}"""
        .formatted(plantId, nickname, roomId, lastWateredAt);
  }

  private static String updatePayload(String nickname, Long roomId, String lastWateredAt) {
    return """
        {"nickname":"%s","roomId":%d,"lastWateredAt":"%s"}"""
        .formatted(nickname, roomId, lastWateredAt);
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
