package com.dendi.peony.controller.plant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.dendi.peony.TestcontainersConfiguration;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantImage;
import com.dendi.peony.entity.PlantImageSource;
import com.dendi.peony.entity.PlantNetSpeciesImage;
import com.dendi.peony.entity.PlantSource;
import com.dendi.peony.repository.PlantImageRepository;
import com.dendi.peony.repository.PlantNetSpeciesImageRepository;
import com.dendi.peony.repository.PlantRepository;
import com.dendi.peony.repository.UserPlantRepository;
import com.dendi.peony.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
 * search over name/scientific name/tags, manual registration, and tier-filtered detail.
 *
 * <p>Registration finds or creates the catalog entry and always creates a new individual, so the
 * response identity is the individual's id (with the catalog plant id in {@code plantId}).
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

  @Autowired private PlantImageRepository plantImageRepository;

  @Autowired private PlantNetSpeciesImageRepository plantNetSpeciesImageRepository;

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
   * Manual registration creates the catalog entry and one individual for the registering user, and
   * returns the individual with the catalog plant's fields.
   */
  @Test
  void testCreatePlant_CreatesCatalogAndIndividual_WhenValidRequest() throws Exception {
    String token = signupAndSignin("t10-create-1");
    String scientificName = "Ficus t10 manual";
    long before = userPlantRepository.count();

    mockMvc
        .perform(
            post("/api/v1/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(createPlantBody("Fiddle-leaf fig", scientificName, 7))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.commonName").value("Fiddle-leaf fig"))
        .andExpect(jsonPath("$.scientificName").value(scientificName))
        .andExpect(jsonPath("$.waterFrequency").value(7))
        .andExpect(
            jsonPath("$.plantId")
                .value(plantRepository.findByScientificName(scientificName).orElseThrow().getId()));

    Plant stored = plantRepository.findByScientificName(scientificName).orElseThrow();
    Long userId = userRepository.findByUsername("t10-create-1").orElseThrow().getId();
    long individualsForPlant =
        userPlantRepository.findAllWithPlantAndLocationByUserId(userId).stream()
            .filter(row -> row.plant().getId().equals(stored.getId()))
            .count();
    assertEquals(
        1,
        individualsForPlant,
        "registering user must own exactly one individual for the created catalog plant");
    assertEquals(before + 1, userPlantRepository.count(), "exactly one individual must be created");
  }

  /** Registering the same catalog plant twice creates two separate individuals. */
  @Test
  void testCreatePlant_CreatesSecondIndividual_WhenScientificNameAlreadyRegistered()
      throws Exception {
    String token = signupAndSignin("t10-create-duplicate");
    String scientificName = "Ficus t10 duplicate";
    long firstId = registerPlant(token, "Ficus", scientificName);
    long secondId = registerPlant(token, "Ficus", scientificName);

    assertTrue(firstId != secondId, "each registration must create its own individual");
    assertEquals(
        2,
        userPlantRepository
            .findAllWithPlantAndLocationByUserId(
                userRepository.findByUsername("t10-create-duplicate").orElseThrow().getId())
            .size(),
        "both registrations must be listed as separate individuals");
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

  /** Registration without a scientific name (missing or blank) is rejected. */
  @Test
  void testCreatePlant_Returns400_WhenScientificNameMissingOrBlank() throws Exception {
    long before = plantRepository.count();
    String token = signupAndSignin("t10-create-400");

    mockMvc
        .perform(
            post("/api/v1/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"commonName\":\"No science\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());

    mockMvc
        .perform(
            post("/api/v1/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"scientificName\":\"   \"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());

    assertEquals(before, plantRepository.count(), "no plant may be created for an invalid request");
  }

  /**
   * Manual registration requires only the scientific name: the common name and watering frequency
   * are optional and stay absent from the JSON when unknown, while the row records MANUAL origin.
   */
  @Test
  void testCreatePlant_AcceptsScientificNameOnly_WhenOptionalFieldsOmitted() throws Exception {
    String token = signupAndSignin("t38-manual-only");
    String scientificName = "Ficus t38 manual only";

    mockMvc
        .perform(
            post("/api/v1/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"scientificName\":\"" + scientificName + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.scientificName").value(scientificName))
        .andExpect(jsonPath("$.source").value("MANUAL"))
        .andExpect(jsonPath("$.commonName").doesNotExist())
        .andExpect(jsonPath("$.waterFrequency").doesNotExist());

    Plant stored = plantRepository.findByScientificName(scientificName).orElseThrow();
    assertEquals(PlantSource.MANUAL, stored.getSource());
    assertNull(stored.getName());
    assertNull(stored.getWateringFrequency());
    assertEquals("", stored.getDescription());
  }

  /**
   * Registering a scientific name that is already in the catalog reuses the existing entry instead
   * of failing: the response carries the existing plant id and no duplicate catalog row is created.
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
        .andExpect(jsonPath("$.plantId").value(seeded.getId()));

    assertEquals(catalogSize, plantRepository.count(), "no duplicate catalog entry may be created");
  }

  /**
   * A scan registration creates the catalogue entry with identity/taxonomy only and one individual
   * for the caller in the same request: the response carries the individual id, the catalog plant
   * id, PLANTNET_SCAN origin, the placed room, the last-watered date, and no unknown care fields.
   */
  @Test
  void testCreatePlantFromScan_CreatesCatalogueAndIndividual_WhenValidRequest() throws Exception {
    String token = signupAndSignin("t38-fromscan-create");
    long userId = userId("t38-fromscan-create");
    long roomId = createHouseAndRoom(token, "Scan house");
    String scientificName = "Caladium t38 bicolor";

    mockMvc
        .perform(
            post("/api/v1/plants/from-scan")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    fromScanBody(scientificName, "Heart of Jesus", "Araceae", "Bicolor", roomId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.commonName").value("Heart of Jesus"))
        .andExpect(jsonPath("$.scientificName").value(scientificName))
        .andExpect(jsonPath("$.family").value("Araceae"))
        .andExpect(jsonPath("$.source").value("PLANTNET_SCAN"))
        .andExpect(jsonPath("$.nickname").value("Bicolor"))
        .andExpect(jsonPath("$.lastWateredAt").value("2026-10-30T09:15:00"))
        .andExpect(jsonPath("$.nextWaterDueAt").doesNotExist())
        .andExpect(jsonPath("$.waterFrequency").doesNotExist())
        .andExpect(jsonPath("$.humidity").doesNotExist())
        .andExpect(jsonPath("$.room.id").value(roomId))
        .andExpect(jsonPath("$.plantId").value(plantId(scientificName)));

    Plant stored = plantRepository.findByScientificName(scientificName).orElseThrow();
    assertEquals(PlantSource.PLANTNET_SCAN, stored.getSource());
    assertEquals("", stored.getDescription());
    assertNull(stored.getWateringFrequency());
    assertEquals(1, individualCount(userId, stored.getId()));
  }

  /**
   * Repeated scans of one species reuse a single catalogue row: only blank common-name/family
   * values are filled in, and every scan still creates a new individual for the caller.
   */
  @Test
  void testCreatePlantFromScan_ReusesCatalogueRowAndFillsOnlyBlanks_WhenScannedRepeatedly()
      throws Exception {
    String token = signupAndSignin("t38-fromscan-repeat");
    long userId = userId("t38-fromscan-repeat");
    String scientificName = "Caladium t38 repeat";

    mockMvc
        .perform(
            post("/api/v1/plants/from-scan")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {"scientificName":"%s","commonName":"   ","family":"  "}"""
                        .formatted(scientificName))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.commonName").doesNotExist())
        .andExpect(jsonPath("$.family").doesNotExist());

    mockMvc
        .perform(
            post("/api/v1/plants/from-scan")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(fromScanBody(scientificName, "Heart of Jesus", "Araceae", null, null))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            post("/api/v1/plants/from-scan")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(fromScanBody(scientificName, "Other name", "Other family", null, null))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.commonName").value("Heart of Jesus"))
        .andExpect(jsonPath("$.family").value("Araceae"));

    long catalogueRows =
        plantRepository.findAll().stream()
            .filter(plant -> scientificName.equals(plant.getScientificName()))
            .count();
    assertEquals(1, catalogueRows, "a repeated scan must reuse the catalogue row");

    Plant stored = plantRepository.findByScientificName(scientificName).orElseThrow();
    assertEquals("Heart of Jesus", stored.getName(), "only blank fields may be filled");
    assertEquals("Araceae", stored.getFamily(), "only blank fields may be filled");
    assertEquals(PlantSource.PLANTNET_SCAN, stored.getSource());
    assertEquals(3, individualCount(userId, stored.getId()), "every scan creates an individual");
  }

  /**
   * A catalogue row first created manually keeps its MANUAL origin and populated fields when the
   * same species is later scanned.
   */
  @Test
  void testCreatePlantFromScan_KeepsManualOriginAndFields_WhenCatalogueRowWasManual()
      throws Exception {
    String token = signupAndSignin("t38-fromscan-manual-origin");
    String scientificName = "Caladium t38 manual origin";

    mockMvc
        .perform(
            post("/api/v1/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {"commonName":"Manual name","scientificName":"%s","family":"Manual family","waterFrequency":7}"""
                        .formatted(scientificName))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.source").value("MANUAL"));

    mockMvc
        .perform(
            post("/api/v1/plants/from-scan")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(fromScanBody(scientificName, "Scan name", "Scan family", null, null))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.source").value("MANUAL"))
        .andExpect(jsonPath("$.commonName").value("Manual name"))
        .andExpect(jsonPath("$.family").value("Manual family"))
        .andExpect(jsonPath("$.waterFrequency").value(7))
        .andExpect(
            jsonPath("$.nextWaterDueAt")
                .value("2026-11-06T09:15:00")); // reused frequency schedules the new individual

    Plant stored = plantRepository.findByScientificName(scientificName).orElseThrow();
    assertEquals(PlantSource.MANUAL, stored.getSource(), "source must record the creation origin");
    assertEquals("Manual name", stored.getName());
    assertEquals("Manual family", stored.getFamily());
    assertEquals(7, stored.getWateringFrequency());
  }

  /**
   * Manual registration reuses a scan-created row without filling its blanks or reclassifying its
   * origin.
   */
  @Test
  void testCreatePlant_KeepsScanOriginAndBlanks_WhenCatalogueRowWasScanCreated() throws Exception {
    String token = signupAndSignin("t38-manual-after-scan");
    String scientificName = "Caladium t38 scan origin";

    mockMvc
        .perform(
            post("/api/v1/plants/from-scan")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"scientificName\":\"" + scientificName + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.source").value("PLANTNET_SCAN"));

    mockMvc
        .perform(
            post("/api/v1/plants")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {"commonName":"Manual name","scientificName":"%s","waterFrequency":7}"""
                        .formatted(scientificName))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.source").value("PLANTNET_SCAN"))
        .andExpect(jsonPath("$.commonName").doesNotExist())
        .andExpect(jsonPath("$.waterFrequency").doesNotExist());

    Plant stored = plantRepository.findByScientificName(scientificName).orElseThrow();
    assertEquals(PlantSource.PLANTNET_SCAN, stored.getSource());
    assertNull(stored.getName(), "manual registration must not fill a scan row's blanks");
    assertNull(
        stored.getWateringFrequency(), "manual registration must not fill a scan row's blanks");
  }

  /** A scan without a usable scientific name is rejected and writes nothing. */
  @Test
  void testCreatePlantFromScan_Returns400_WhenScientificNameMissingOrBlank() throws Exception {
    long before = plantRepository.count();
    String token = signupAndSignin("t38-fromscan-400");

    mockMvc
        .perform(
            post("/api/v1/plants/from-scan")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());

    mockMvc
        .perform(
            post("/api/v1/plants/from-scan")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"scientificName\":\"   \"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());

    assertEquals(
        before, plantRepository.count(), "an invalid scan must not create a catalogue row");
  }

  /** A malformed last-watered date rolls the whole scan registration back. */
  @Test
  void testCreatePlantFromScan_Returns400_WhenLastWateredAtMalformed() throws Exception {
    String token = signupAndSignin("t38-fromscan-bad-date");
    String scientificName = "Caladium t38 bad date";

    mockMvc
        .perform(
            post("/api/v1/plants/from-scan")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(
                    """
                    {"scientificName":"%s","lastWateredAt":"not-a-date"}"""
                        .formatted(scientificName))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").exists());

    assertTrue(
        plantRepository.findByScientificName(scientificName).isEmpty(),
        "the catalogue upsert must roll back with the failed individual");
  }

  /** A room outside the caller's houses is a 404 and rolls the whole registration back. */
  @Test
  void testCreatePlantFromScan_Returns404_WhenRoomBelongsToAnotherUser() throws Exception {
    String ownerToken = signupAndSignin("t38-fromscan-room-owner");
    long foreignRoomId = createHouseAndRoom(ownerToken, "Other user's house");
    String token = signupAndSignin("t38-fromscan-room-outsider");
    long userId = userId("t38-fromscan-room-outsider");
    String scientificName = "Caladium t38 foreign room";

    mockMvc
        .perform(
            post("/api/v1/plants/from-scan")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content(fromScanBody(scientificName, null, null, null, foreignRoomId))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("Room not found"));

    assertTrue(
        plantRepository.findByScientificName(scientificName).isEmpty(),
        "a failed location must roll the catalogue upsert back");
    assertTrue(
        userPlantRepository.findAllWithPlantAndLocationByUserId(userId).isEmpty(),
        "a failed location must create no individual");
  }

  /**
   * Two concurrent scans of the same new species must not fail on the unique scientific name: the
   * upsert yields one catalogue row and each request still gets its individual.
   */
  @Test
  void testCreatePlantFromScan_CreatesSingleCatalogueRow_WhenCalledConcurrently() throws Exception {
    String token = signupAndSignin("t38-fromscan-race");
    long userId = userId("t38-fromscan-race");
    String scientificName = "Caladium t38 race";
    String body = "{\"scientificName\":\"" + scientificName + "\"}";

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      List<Future<Integer>> results = new ArrayList<>();
      for (int i = 0; i < 2; i++) {
        results.add(
            executor.submit(
                () -> {
                  start.await();
                  return mockMvc
                      .perform(
                          post("/api/v1/plants/from-scan")
                              .contentType(MediaType.APPLICATION_JSON_VALUE)
                              .content(body)
                              .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                      .andReturn()
                      .getResponse()
                      .getStatus();
                }));
      }
      start.countDown();
      for (Future<Integer> result : results) {
        assertEquals(201, result.get(30, TimeUnit.SECONDS), "both concurrent scans must succeed");
      }
    } finally {
      executor.shutdownNow();
    }

    long catalogueRows =
        plantRepository.findAll().stream()
            .filter(plant -> scientificName.equals(plant.getScientificName()))
            .count();
    assertEquals(1, catalogueRows, "the upsert must collapse the race into one catalogue row");
    Plant stored = plantRepository.findByScientificName(scientificName).orElseThrow();
    assertEquals(2, individualCount(userId, stored.getId()));
  }

  /**
   * A staged candidate whose download is refused (here: a URL outside the allowlisted origin) is
   * best effort: the scan still answers 201 with no image, and the candidate stays staged for a
   * later retry.
   */
  @Test
  void testCreatePlantFromScan_ReturnsCreatedWithoutImage_WhenStagedDownloadFails()
      throws Exception {
    String token = signupAndSignin("t45-fromscan-download-fail");
    String scientificName = "Caladium t45 download fail";
    PlantNetSpeciesImage staged = new PlantNetSpeciesImage();
    staged.setScientificName(scientificName.toLowerCase(Locale.ROOT));
    staged.setImageUrl("https://evil.example/image/m/abc");
    staged.setLicense("cc-by");
    staged.setStagedAt(LocalDateTime.now());
    plantNetSpeciesImageRepository.save(staged);

    mockMvc
        .perform(
            post("/api/v1/plants/from-scan")
                .contentType(MediaType.APPLICATION_JSON_VALUE)
                .content("{\"scientificName\":\"" + scientificName + "\"}")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.scientificName").value(scientificName))
        .andExpect(jsonPath("$.hasImage").value(false));

    Plant stored = plantRepository.findByScientificName(scientificName).orElseThrow();
    assertTrue(
        plantImageRepository.findByPlantIdOrderByPositionAsc(stored.getId()).isEmpty(),
        "a failed download must leave the catalogue row image-less");
    assertTrue(
        plantNetSpeciesImageRepository.findById(staged.getId()).isPresent(),
        "a failed download must keep the candidate staged for a later scan");
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
        .andExpect(jsonPath("$.source").value("MANUAL"))
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
        .andExpect(jsonPath("$.source").value("MANUAL"))
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

  /**
   * Detail carries the catalogue image attribution as credits ascending by position, with the
   * PlantNet fallback's source, author, licence, and citation.
   */
  @Test
  void testGetPlant_ReturnsImageCredits_WhenPlantHasAttributedImages() throws Exception {
    Plant plant = seedPlant("Monstera", "Monstera t45 detail credits", fullTags());
    seedImage(plant, 1, PlantImageSource.SEED, "Seed author", "cc0", "Seed citation");
    seedImage(plant, 0, PlantImageSource.PLANTNET, "Jane Doe", "cc-by-sa", "PlantNet citation");
    String token = signupAndSignin("t45-detail-credits");

    mockMvc
        .perform(
            get("/api/v1/plants/" + plant.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageCredits.length()").value(2))
        .andExpect(jsonPath("$.imageCredits[0].position").value(0))
        .andExpect(jsonPath("$.imageCredits[0].source").value("PLANTNET"))
        .andExpect(jsonPath("$.imageCredits[0].author").value("Jane Doe"))
        .andExpect(jsonPath("$.imageCredits[0].license").value("cc-by-sa"))
        .andExpect(jsonPath("$.imageCredits[0].citation").value("PlantNet citation"))
        .andExpect(jsonPath("$.imageCredits[1].position").value(1))
        .andExpect(jsonPath("$.imageCredits[1].source").value("SEED"));
  }

  /**
   * A row that predates attribution keeps its position but omits the unknown credit fields (absent,
   * not null).
   */
  @Test
  void testGetPlant_OmitsLegacyAttribution_WhenImageHasNoCredit() throws Exception {
    Plant plant = seedPlant("Monstera", "Monstera t45 detail legacy", fullTags());
    seedImage(plant, 0, null, null, null, null);
    String token = signupAndSignin("t45-detail-legacy");

    mockMvc
        .perform(
            get("/api/v1/plants/" + plant.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageCredits.length()").value(1))
        .andExpect(jsonPath("$.imageCredits[0].position").value(0))
        .andExpect(jsonPath("$.imageCredits[0].source").doesNotExist())
        .andExpect(jsonPath("$.imageCredits[0].author").doesNotExist())
        .andExpect(jsonPath("$.imageCredits[0].license").doesNotExist())
        .andExpect(jsonPath("$.imageCredits[0].citation").doesNotExist());
  }

  /** A plant without catalogue images omits the credits field entirely. */
  @Test
  void testGetPlant_OmitsImageCredits_WhenPlantHasNoImages() throws Exception {
    Plant plant = seedPlant("Monstera", "Monstera t45 detail no image", fullTags());
    String token = signupAndSignin("t45-detail-no-image");

    mockMvc
        .perform(
            get("/api/v1/plants/" + plant.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.imageCredits").doesNotExist());
  }

  /** Search rows carry the same ascending credits as the detail row. */
  @Test
  void testListPlants_ReturnsImageCredits_WhenMatchesHaveAttributedImages() throws Exception {
    Plant plant = seedPlant("Monstera", "Monstera t45 search credits", fullTags());
    seedImage(plant, 1, PlantImageSource.SEED, "Seed author", "cc0", null);
    seedImage(plant, 0, PlantImageSource.PLANTNET, "Jane Doe", "cc-by", null);
    String token = signupAndSignin("t45-search-credits");

    List<JsonNode> body = searchBody(token, "Monstera t45 search credits");
    JsonNode row =
        body.stream()
            .filter(
                node -> "Monstera t45 search credits".equals(node.path("scientificName").asText()))
            .findFirst()
            .orElseThrow();
    assertEquals(2, row.path("imageCredits").size(), "expected both credits in " + row);
    assertEquals(0, row.path("imageCredits").get(0).path("position").asInt());
    assertEquals("PLANTNET", row.path("imageCredits").get(0).path("source").asText());
    assertEquals("Jane Doe", row.path("imageCredits").get(0).path("author").asText());
    assertEquals(1, row.path("imageCredits").get(1).path("position").asInt());
    assertEquals("SEED", row.path("imageCredits").get(1).path("source").asText());
  }

  /** Search rows without catalogue images keep the credits field absent. */
  @Test
  void testListPlants_OmitsImageCredits_WhenMatchHasNoImages() throws Exception {
    seedPlant("Monstera", "Monstera t45 search no image", fullTags());
    String token = signupAndSignin("t45-search-no-image");

    List<JsonNode> body = searchBody(token, "Monstera t45 search no image");
    JsonNode row =
        body.stream()
            .filter(
                node -> "Monstera t45 search no image".equals(node.path("scientificName").asText()))
            .findFirst()
            .orElseThrow();
    assertFalse(row.has("imageCredits"), "a plant without images must omit imageCredits");
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
    plant.setSource(PlantSource.MANUAL);
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

  /** Seeds an attributed catalogue image row at a gallery position. */
  private void seedImage(
      Plant plant,
      int position,
      PlantImageSource source,
      String author,
      String license,
      String citation) {
    PlantImage image = new PlantImage();
    image.setPlantId(plant.getId());
    image.setPosition(position);
    image.setFilename("seeded-" + plant.getId() + "-" + position + ".jpg");
    image.setSource(source);
    image.setAuthor(author);
    image.setLicense(license);
    image.setCitation(citation);
    image.setCreatedAt(LocalDateTime.now());
    plantImageRepository.save(image);
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

  /** Registers a plant through the REST endpoint and returns the individual id. */
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

  /** Builds a from-scan payload, omitting null optional fields. */
  private static String fromScanBody(
      String scientificName, String commonName, String family, String nickname, Long roomId) {
    StringBuilder body = new StringBuilder("{\"scientificName\":\"" + scientificName + "\"");
    if (commonName != null) {
      body.append(",\"commonName\":\"").append(commonName).append('"');
    }
    if (family != null) {
      body.append(",\"family\":\"").append(family).append('"');
    }
    if (nickname != null) {
      body.append(",\"nickname\":\"").append(nickname).append('"');
    }
    if (roomId != null) {
      body.append(",\"roomId\":").append(roomId);
    }
    body.append(",\"lastWateredAt\":\"2026-10-30T09:15:00\"");
    return body.append('}').toString();
  }

  /** Creates a house and one room for the caller and returns the room id. */
  private long createHouseAndRoom(String token, String houseName) throws Exception {
    MvcResult house =
        mockMvc
            .perform(
                post("/api/v1/me/houses")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content("{\"name\":\"" + houseName + "\"}")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();
    long houseId = JSON.readTree(house.getResponse().getContentAsString()).get("id").asLong();

    MvcResult room =
        mockMvc
            .perform(
                post("/api/v1/houses/" + houseId + "/rooms")
                    .contentType(MediaType.APPLICATION_JSON_VALUE)
                    .content("{\"name\":\"Kitchen\"}")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
            .andExpect(status().isCreated())
            .andReturn();
    return JSON.readTree(room.getResponse().getContentAsString()).get("id").asLong();
  }

  private long userId(String username) {
    return userRepository.findByUsername(username).orElseThrow().getId();
  }

  private long plantId(String scientificName) {
    return plantRepository.findByScientificName(scientificName).orElseThrow().getId();
  }

  private long individualCount(long userId, long plantId) {
    return userPlantRepository.findAllWithPlantAndLocationByUserId(userId).stream()
        .filter(row -> row.plant().getId() == plantId)
        .count();
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
