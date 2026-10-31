package com.dendi.peony.controller.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.repository.UserPlantRepository;
import com.dendi.peony.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
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
 * Tests for the plant CRUD endpoints at the REST seam, against a real PostgreSQL database: typahead
 * search over name/scientific name/tags, manual registration, tier-filtered detail, and per-plant
 * notes (user_plants.nickname).
 *
 * <p>Tests share one database, so each test uses its own account and a scientific name no other
 * test registers; list assertions filter the parsed response body by scientific name so they hold
 * regardless of test order.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
class PlantsControllerTest {

  private static final ObjectMapper JSON = new ObjectMapper();

  @Autowired private MockMvc mockMvc;

  @Autowired private PlantRepository plantRepository;

  @Autowired private UserPlantRepository userPlantRepository;

  @Autowired private UserRepository userRepository;

  /** Plant routes are protected: every endpoint rejects requests without a bearer token. */
  @Test
  void testListPlants_Returns401_WhenNoBearer() throws Exception {
    mockMvc.perform(get("/api/v1/plants")).andExpect(status().isUnauthorized());
  }

  /** Plant routes are protected: every endpoint rejects requests without a bearer token. */
  @Test
  void testCreatePlant_Returns401_WhenNoBearer() throws Exception {
    mockMvc
        .perform(post("/api/v1/plants").contentType(MediaType.APPLICATION_JSON_VALUE).content("{}"))
        .andExpect(status().isUnauthorized());
  }

  /** Plant routes are protected: every endpoint rejects requests without a bearer token. */
  @Test
  void testGetPlant_Returns401_WhenNoBearer() throws Exception {
    mockMvc
        .perform(get("/api/v1/plants/1"))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.error").value("Authentication required"));
  }

  /** Plant routes are protected: every endpoint rejects requests without a bearer token. */
  @Test
  void testUpdateNotes_Returns401_WhenNoBearer() throws Exception {
    mockMvc
        .perform(
            patch("/api/v1/plants/1/notes")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"notes\":\"hi\"}"))
        .andExpect(status().isUnauthorized());
  }

  /** Typahead matches the common name case-insensitively on a partial query. */
  @Test
  void testListPlants_MatchesName_WhenQueryPartiallyMatches() throws Exception {
    seedPlant("Swiss Cheese Plant", "Monstera deliciosa", new String[] {"tropical"});
    String token = signupAndSignin("t10-typahead-name");

    List<JsonNode> body = searchBody(token, "swiss cheese");
    assertTrue(
        body.stream()
            .anyMatch(node -> "Monstera deliciosa".equals(node.path("scientificName").asText())),
        "expected the name match in " + body);
  }

  /** Typahead matches the scientific name case-insensitively on a partial query. */
  @Test
  void testListPlants_MatchesScientificName_WhenQueryPartiallyMatches() throws Exception {
    seedPlant("Snake Plant", "Dracaena trifasciata", new String[] {});
    String token = signupAndSignin("t10-typahead-sci");

    List<JsonNode> body = searchBody(token, "trifasciata");
    assertTrue(
        body.stream()
            .anyMatch(node -> "Dracaena trifasciata".equals(node.path("scientificName").asText())),
        "expected the scientific name match in " + body);
  }

  /** Typahead matches whole tags through the GIN-indexed containment operator. */
  @Test
  void testListPlants_MatchesTag_WhenQueryEqualsTag() throws Exception {
    seedPlant("Snake Plant", "Dracaena t10 tag match", new String[] {"low light", "pet friendly"});
    String token = signupAndSignin("t10-typahead-tag");

    List<JsonNode> body = searchBody(token, "low light");
    assertTrue(
        body.stream()
            .anyMatch(
                node -> "Dracaena t10 tag match".equals(node.path("scientificName").asText())),
        "expected the tag match in " + body);
  }

  /** A query matching nothing returns an empty list, not an error. */
  @Test
  void testListPlants_ReturnsEmptyList_WhenQueryMatchesNothing() throws Exception {
    String token = signupAndSignin("t10-typahead-none");

    assertTrue(
        searchBody(token, "zzz-no-such-plant-zzz").isEmpty(),
        "no plant may match a query that matches nothing");
  }

  /** Without a query the endpoint lists the whole catalog. */
  @Test
  void testListPlants_ReturnsWholeCatalog_WhenNoQueryProvided() throws Exception {
    long catalogSize = plantRepository.count();
    String token = signupAndSignin("t10-typahead-all");

    assertEquals(catalogSize, searchBody(token, null).size(), "the whole catalog must be listed");
  }

  /**
   * Manual registration creates the catalog entry and links it to the registering user, and returns
   * the created plant.
   */
  @Test
  void testCreatePlant_CreatesCatalogAndUserLink_WhenValidRequest() throws Exception {
    String token = signupAndSignin("t10-create-1");
    String scientificName = "Ficus t10 manual";

    mockMvc
        .perform(
            post("/api/v1/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(createPlantBody("Fiddle-leaf fig", scientificName, 7))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.commonName").value("Fiddle-leaf fig"))
        .andExpect(jsonPath("$.scientificName").value(scientificName))
        .andExpect(jsonPath("$.waterFrequency").value(7));

    Plant stored = plantRepository.findByScientificName(scientificName).orElseThrow();
    Long userId = userRepository.findByUsername("t10-create-1").orElseThrow().getId();
    assertTrue(
        userPlantRepository.findByUserIdAndPlantId(userId, stored.getId()).isPresent(),
        "registering user must be linked to the created plant");
  }

  /** Free-tier registration responses omit the premium-only fields (absent, not null). */
  @Test
  void testCreatePlant_OmitsPremiumFields_WhenFreeTierUser() throws Exception {
    String token = signupAndSignin("t10-create-free");

    mockMvc
        .perform(
            post("/api/v1/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(fullCreatePlantBody("Ficus t10 free tier"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.commonName").exists())
        .andExpect(jsonPath("$.waterFrequency").exists())
        .andExpect(jsonPath("$.humidity").doesNotExist())
        .andExpect(jsonPath("$.tempRange").doesNotExist())
        .andExpect(jsonPath("$.difficulty").doesNotExist())
        .andExpect(jsonPath("$.nativeRegion").doesNotExist())
        .andExpect(jsonPath("$.matureSize").doesNotExist());
  }

  /** Premium-tier registration responses carry the full plant object. */
  @Test
  void testCreatePlant_ReturnsFullPlant_WhenPremiumTierUser() throws Exception {
    String token = signupAndSignin("t10-create-premium");
    upgrade(token);

    mockMvc
        .perform(
            post("/api/v1/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(fullCreatePlantBody("Ficus t10 premium tier"))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.humidity").value("high"))
        .andExpect(jsonPath("$.tempRange").value("18-28C"))
        .andExpect(jsonPath("$.difficulty").value("easy"))
        .andExpect(jsonPath("$.nativeRegion").value("South America"))
        .andExpect(jsonPath("$.matureSize").value("3m tall"));
  }

  /** Registration without the required identity/water fields is rejected. */
  @Test
  void testCreatePlant_Returns400_WhenRequiredFieldsMissing() throws Exception {
    long before = plantRepository.count();
    String token = signupAndSignin("t10-create-400");

    mockMvc
        .perform(
            post("/api/v1/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"commonName\":\"No water\",\"scientificName\":\"Ficus t10 invalid\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());

    assertEquals(before, plantRepository.count(), "no plant may be created for an invalid request");
  }

  /**
   * Registering a scientific name that is already in the catalog reuses the existing entry instead
   * of failing: the response is the existing plant and no duplicate row is created.
   */
  @Test
  void testCreatePlant_ReusesCatalogEntry_WhenScientificNameAlreadyRegistered() throws Exception {
    Plant seeded = seedPlant("Monstera", "Monstera t10 reuse", new String[] {"tropical"});
    long catalogSize = plantRepository.count();
    String token = signupAndSignin("t10-create-reuse");

    mockMvc
        .perform(
            post("/api/v1/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(createPlantBody("Monstera", "Monstera t10 reuse", 7))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(seeded.getId()));

    assertEquals(catalogSize, plantRepository.count(), "no duplicate catalog entry may be created");
  }

  /** Detail returns the free field set for free-tier users: premium fields are absent. */
  @Test
  void testGetPlant_ReturnsFreeFieldSet_WhenFreeTierUser() throws Exception {
    seedPlant("Monstera", "Monstera t10 detail free", fullTags());
    String token = signupAndSignin("t10-detail-free");

    mockMvc
        .perform(
            plantIdPath("Monstera t10 detail free")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.commonName").value("Monstera"))
        .andExpect(jsonPath("$.family").value("Araceae"))
        .andExpect(jsonPath("$.tags.length()").value(3))
        .andExpect(jsonPath("$.lightNeeds").value("bright indirect"))
        .andExpect(jsonPath("$.waterFrequency").value(7))
        .andExpect(jsonPath("$.toPets").value(true))
        .andExpect(jsonPath("$.toHumans").value(false))
        .andExpect(jsonPath("$.humidity").doesNotExist())
        .andExpect(jsonPath("$.tempRange").doesNotExist())
        .andExpect(jsonPath("$.difficulty").doesNotExist())
        .andExpect(jsonPath("$.nativeRegion").doesNotExist())
        .andExpect(jsonPath("$.matureSize").doesNotExist());
  }

  /** Detail returns the full plant object for premium-tier users. */
  @Test
  void testGetPlant_ReturnsFullPlant_WhenPremiumTierUser() throws Exception {
    seedPlant("Monstera", "Monstera t10 detail premium", fullTags());
    String token = signupAndSignin("t10-detail-premium");
    upgrade(token);

    mockMvc
        .perform(
            plantIdPath("Monstera t10 detail premium")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.humidity").value("high"))
        .andExpect(jsonPath("$.tempRange").value("18-28C"))
        .andExpect(jsonPath("$.difficulty").value("easy"))
        .andExpect(jsonPath("$.nativeRegion").value("South America"))
        .andExpect(jsonPath("$.matureSize").value("3m tall"));
  }

  /** Detail of an unknown plant is a 404. */
  @Test
  void testGetPlant_Returns404_WhenPlantNotFound() throws Exception {
    String token = signupAndSignin("t10-detail-404");

    mockMvc
        .perform(get("/api/v1/plants/9999999").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /** Updating notes stores the text in the user's per-plant record and echoes it back. */
  @Test
  void testUpdateNotes_ReturnsUpdatedNote_WhenUserUpdatesNote() throws Exception {
    String token = signupAndSignin("t10-notes-1");
    long plantId = registerPlant(token, "Ficus t10 notes", "Ficus t10 notes");

    mockMvc
        .perform(
            notesRequest(plantId, "window plant")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notes").value("window plant"));

    Long userId = userRepository.findByUsername("t10-notes-1").orElseThrow().getId();
    assertEquals(
        "window plant",
        userPlantRepository.findByUserIdAndPlantId(userId, plantId).orElseThrow().getNickname());
  }

  /** Notes work for catalog plants the user has not registered: the link is created on demand. */
  @Test
  void testUpdateNotes_CreatesUserLink_WhenUserHasNoLinkYet() throws Exception {
    Plant seeded = seedPlant("Snake Plant", "Dracaena t10 notes unlinked", new String[] {});
    String token = signupAndSignin("t10-notes-2");
    Long userId = userRepository.findByUsername("t10-notes-2").orElseThrow().getId();

    mockMvc
        .perform(
            notesRequest(seeded.getId(), "keep away from the cat")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.notes").value("keep away from the cat"));

    assertEquals(
        "keep away from the cat",
        userPlantRepository
            .findByUserIdAndPlantId(userId, seeded.getId())
            .orElseThrow()
            .getNickname());
  }

  /** Notes for an unknown plant is a 404. */
  @Test
  void testUpdateNotes_Returns404_WhenPlantNotFound() throws Exception {
    String token = signupAndSignin("t10-notes-404");

    mockMvc
        .perform(
            notesRequest(9999999, "any note").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").exists());
  }

  /** A notes payload without the note text is rejected. */
  @Test
  void testUpdateNotes_Returns400_WhenNotesMissing() throws Exception {
    String token = signupAndSignin("t10-notes-400");
    long plantId = registerPlant(token, "Ficus t10 notes 400", "Ficus t10 notes 400");

    mockMvc
        .perform(
            patch("/api/v1/plants/" + plantId + "/notes")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());
  }

  /** Free-tier list responses omit the premium-only fields per plant. */
  @Test
  void testListPlants_OmitsPremiumFields_WhenFreeTierUser() throws Exception {
    seedPlant("Monstera", "Monstera t10 list free", fullTags());
    String token = signupAndSignin("t10-list-free");

    List<JsonNode> body = searchBody(token, "Monstera t10 list free");
    Optional<JsonNode> plant =
        body.stream()
            .filter(node -> "Monstera t10 list free".equals(node.path("scientificName").asText()))
            .findFirst();
    assertTrue(plant.isPresent(), "expected the seeded plant in " + body);
    JsonNode node = plant.orElseThrow();
    assertTrue(node.has("lightNeeds"), "free-tier fields must be present");
    assertFalse(node.has("humidity"), "premium-only fields must be absent, not null");
    assertFalse(node.has("tempRange"), "premium-only fields must be absent, not null");
    assertFalse(node.has("difficulty"), "premium-only fields must be absent, not null");
    assertFalse(node.has("nativeRegion"), "premium-only fields must be absent, not null");
    assertFalse(node.has("matureSize"), "premium-only fields must be absent, not null");
  }

  /** Seeds a fully populated catalog plant so tier visibility can be asserted. */
  private Plant seedPlant(String name, String scientificName, String[] tags) {
    Plant plant = new Plant();
    plant.setName(name);
    plant.setScientificName(scientificName);
    plant.setDescription("seeded for testing");
    plant.setWateringFrequency(7);
    plant.setFamily("Araceae");
    plant.setTags(tags);
    plant.setLightNeeds("bright indirect");
    plant.setHumidity("high");
    plant.setTempRange("18-28C");
    plant.setToPets(true);
    plant.setToHumans(false);
    plant.setDifficulty("easy");
    plant.setNativeRegion("South America");
    plant.setMatureSize("3m tall");
    plant.setCreatedAt(LocalDateTime.now());
    return plantRepository.save(plant);
  }

  /** Runs the typahead endpoint and parses the response body. */
  private List<JsonNode> searchBody(String token, String q) throws Exception {
    MockHttpServletRequestBuilder request =
        get("/api/v1/plants").header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    if (q != null) {
      request = request.param("q", q);
    }
    MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
    return List.of(JSON.readValue(result.getResponse().getContentAsString(), JsonNode[].class));
  }

  /** Registers a plant through the REST endpoint and returns its id. */
  private long registerPlant(String token, String name, String scientificName) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/plants")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content(createPlantBody(name, scientificName, 7))
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();
    return JSON.readTree(result.getResponse().getContentAsString()).get("id").asLong();
  }

  /** Builds the plant detail request for the plant with the given scientific name. */
  private MockHttpServletRequestBuilder plantIdPath(String scientificName) {
    Plant plant = plantRepository.findByScientificName(scientificName).orElseThrow();
    return get("/api/v1/plants/" + plant.getId());
  }

  private MockHttpServletRequestBuilder notesRequest(long plantId, String notes) {
    return patch("/api/v1/plants/" + plantId + "/notes")
        .contentType(MediaType.APPLICATION_JSON_VALUE)
        .content("{\"notes\":\"" + notes + "\"}");
  }

  private static String[] fullTags() {
    return new String[] {"tropical", "low light", "pet friendly"};
  }

  /** Minimal registration payload: the three required fields. */
  private static String createPlantBody(
      String commonName, String scientificName, int waterFrequency) {
    return """
        {"commonName":"%s","scientificName":"%s","waterFrequency":%d}"""
        .formatted(commonName, scientificName, waterFrequency);
  }

  /** Registration payload carrying the full field set, including the premium-only fields. */
  private static String fullCreatePlantBody(String scientificName) {
    return """
        {"commonName":"Ficus","scientificName":"%s","family":"Araceae","tags":["indoor"],
         "lightNeeds":"bright indirect","waterFrequency":7,"toPets":true,"toHumans":false,
         "humidity":"high","tempRange":"18-28C","difficulty":"easy",
         "nativeRegion":"South America","matureSize":"3m tall"}"""
        .formatted(scientificName);
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
