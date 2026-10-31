package com.dendi.peony.controller.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.House;
import com.dendi.peony.entity.Room;
import com.dendi.peony.repository.HouseRepository;
import com.dendi.peony.repository.RoomRepository;
import com.dendi.peony.repository.UserRepository;
import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
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
 * Tests for GET /api/v1/me/houses at the REST seam, against a real PostgreSQL database: only the
 * caller's houses, their nested rooms, case-insensitive ordering at both levels, and batched room
 * loading.
 *
 * <p>Tests share one database, so each test uses its own account and unique house names.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class MeHousesControllerTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @Autowired private UserRepository userRepository;

  @Autowired private HouseRepository houseRepository;

  @Autowired private RoomRepository roomRepository;

  @Autowired private EntityManagerFactory entityManagerFactory;

  /** The endpoint is protected: no bearer token means the standard 401 envelope. */
  @Test
  void testListMyHouses_Returns401_WhenNoBearer() throws Exception {
    mockMvc
        .perform(get("/api/v1/me/houses"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /** An authenticated caller with no houses gets an empty list, not an error. */
  @Test
  void testListMyHouses_ReturnsEmptyList_WhenCallerHasNoHouses() throws Exception {
    String token = signupAndSignin("t26-houses-empty");

    assertTrue(myHouses(token).isEmpty(), "a caller with no houses must get an empty list");
  }

  /** Every house nests only its own rooms, using the existing room response shape. */
  @Test
  void testListMyHouses_ReturnsHousesWithNestedRooms_WhenCallerHasSeveralHouses() throws Exception {
    String username = "t26-houses-nested";
    String token = signupAndSignin(username);
    House home = seedHouse(username, "Home");
    House shed = seedHouse(username, "Shed");
    Room kitchen = seedRoom(home, "Kitchen");
    Room livingRoom = seedRoom(home, "Living room");
    Room porch = seedRoom(shed, "Porch");

    Map<Long, JsonNode> housesById =
        myHouses(token).stream()
            .collect(Collectors.toMap(node -> node.path("id").asLong(), Function.identity()));

    assertEquals(
        Set.of(home.getId(), shed.getId()),
        housesById.keySet(),
        "exactly the caller's houses must be listed");
    assertEquals("Home", housesById.get(home.getId()).path("name").asText());
    assertEquals(
        Set.of(kitchen.getId(), livingRoom.getId()),
        roomIds(housesById.get(home.getId())),
        "each room must be nested under its own house");
    assertEquals(
        Set.of(porch.getId()),
        roomIds(housesById.get(shed.getId())),
        "rooms must not leak into another house");
    JsonNode listedKitchen = roomNode(housesById.get(home.getId()), kitchen.getId());
    assertEquals("Kitchen", listedKitchen.path("name").asText());
    assertEquals(home.getId(), listedKitchen.path("houseId").asLong());
  }

  /** A house without rooms is still listed and carries an empty rooms array. */
  @Test
  void testListMyHouses_ReturnsEmptyRooms_WhenHouseHasNoRooms() throws Exception {
    String username = "t26-houses-no-rooms";
    String token = signupAndSignin(username);
    House home = seedHouse(username, "Home");

    JsonNode house = myHouses(token).get(0);
    assertEquals(home.getId(), house.path("id").asLong());
    assertTrue(house.path("rooms").isArray(), "rooms must always be an array");
    assertEquals(0, house.path("rooms").size(), "a house without rooms must get an empty array");
  }

  /** The listing is scoped to the caller: other users' houses and rooms never appear. */
  @Test
  void testListMyHouses_ReturnsOnlyCallerHouses_WhenOtherUsersHaveHouses() throws Exception {
    String callerUsername = "t26-houses-caller";
    String otherUsername = "t26-houses-other";
    String callerToken = signupAndSignin(callerUsername);
    String otherToken = signupAndSignin(otherUsername);
    House callerHouse = seedHouse(callerUsername, "Caller home");
    Room callerRoom = seedRoom(callerHouse, "Caller kitchen");
    House otherHouse = seedHouse(otherUsername, "Other home");
    seedRoom(otherHouse, "Other balcony");

    List<JsonNode> callerBody = myHouses(callerToken);
    assertEquals(
        Set.of(callerHouse.getId()), houseIds(callerBody), "only the caller's house may be listed");
    assertEquals(
        Set.of(callerRoom.getId()),
        roomIds(callerBody.get(0)),
        "only the caller's rooms may be nested");

    List<JsonNode> otherBody = myHouses(otherToken);
    assertEquals(
        Set.of(otherHouse.getId()),
        houseIds(otherBody),
        "the other user must see only their own house");
  }

  /** House names are ordered case-insensitively ascending. */
  @Test
  void testListMyHouses_OrdersHousesByNameIgnoringCase_WhenNamesDifferInCase() throws Exception {
    String username = "t26-houses-order-houses";
    String token = signupAndSignin(username);
    House cherry = seedHouse(username, "cherry");
    House banana = seedHouse(username, "Banana");
    House apple = seedHouse(username, "apple");

    assertEquals(
        List.of(apple.getId(), banana.getId(), cherry.getId()),
        houseIdsInOrder(myHouses(token)),
        "houses must be ordered case-insensitively by name");
  }

  /** Room names are ordered case-insensitively within each house. */
  @Test
  void testListMyHouses_OrdersRoomsByNameIgnoringCase_WithinEachHouse() throws Exception {
    String username = "t26-houses-order-rooms";
    String token = signupAndSignin(username);
    House home = seedHouse(username, "Home");
    House shed = seedHouse(username, "Shed");
    Room kitchen = seedRoom(home, "kitchen");
    Room balcony = seedRoom(home, "Balcony");
    Room attic = seedRoom(home, "attic");
    Room porch = seedRoom(shed, "Porch");
    Room cellar = seedRoom(shed, "cellar");

    Map<Long, JsonNode> housesById =
        myHouses(token).stream()
            .collect(Collectors.toMap(node -> node.path("id").asLong(), Function.identity()));

    assertEquals(
        List.of(attic.getId(), balcony.getId(), kitchen.getId()),
        roomIdsInOrder(housesById.get(home.getId())),
        "rooms must be ordered case-insensitively by name");
    assertEquals(
        List.of(cellar.getId(), porch.getId()),
        roomIdsInOrder(housesById.get(shed.getId())),
        "the room order must apply within every house");
  }

  /**
   * Room loading is batched: rooms are fetched in one query for all of the caller's house ids, so
   * the statement count does not grow with the number of houses.
   */
  @Test
  void testListMyHouses_LoadsRoomsInOneBatch_WhenCallerHasSeveralHouses() throws Exception {
    String singleUsername = "t26-houses-batch-single";
    String manyUsername = "t26-houses-batch-many";
    String singleToken = signupAndSignin(singleUsername);
    String manyToken = signupAndSignin(manyUsername);
    House singleHouse = seedHouse(singleUsername, "Single home");
    seedRoom(singleHouse, "Single kitchen");
    for (int i = 0; i < 4; i++) {
      House manyHouse = seedHouse(manyUsername, "Many home " + i);
      seedRoom(manyHouse, "Room " + i);
    }

    Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();

    statistics.clear();
    myHouses(singleToken);
    long singleHouseStatements = statistics.getPrepareStatementCount();

    statistics.clear();
    myHouses(manyToken);
    long manyHouseStatements = statistics.getPrepareStatementCount();

    assertEquals(
        singleHouseStatements,
        manyHouseStatements,
        "room loading must use one query for all houses, not one query per house");
  }

  /** Runs the my-houses endpoint and parses the response body. */
  private List<JsonNode> myHouses(String token) throws Exception {
    MvcResult result =
        mockMvc
            .perform(get("/api/v1/me/houses").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();
    return List.of(JSON.readValue(result.getResponse().getContentAsString(), JsonNode[].class));
  }

  private static Set<Long> houseIds(List<JsonNode> body) {
    return body.stream().map(node -> node.path("id").asLong()).collect(Collectors.toSet());
  }

  private static List<Long> houseIdsInOrder(List<JsonNode> body) {
    return body.stream().map(node -> node.path("id").asLong()).toList();
  }

  private static List<Long> roomIdsInOrder(JsonNode house) {
    return house.path("rooms").valueStream().map(room -> room.path("id").asLong()).toList();
  }

  private static Set<Long> roomIds(JsonNode house) {
    return house
        .path("rooms")
        .valueStream()
        .map(room -> room.path("id").asLong())
        .collect(Collectors.toSet());
  }

  private static JsonNode roomNode(JsonNode house, Long roomId) {
    return house
        .path("rooms")
        .valueStream()
        .filter(room -> room.path("id").asLong() == roomId)
        .findFirst()
        .orElseThrow(() -> new AssertionError("room " + roomId + " is not nested under the house"));
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
