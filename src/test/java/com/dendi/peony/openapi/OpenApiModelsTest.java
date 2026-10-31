package com.dendi.peony.openapi;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.dendi.peony.api.model.AuthResponse;
import com.dendi.peony.api.model.Genus;
import com.dendi.peony.api.model.HitResponse;
import com.dendi.peony.api.model.HouseResponse;
import com.dendi.peony.api.model.PlantLocationResponse;
import com.dendi.peony.api.model.PlantRecognitionResult;
import com.dendi.peony.api.model.PlantResponse;
import com.dendi.peony.api.model.ProfileResponse;
import com.dendi.peony.api.model.RecognitionRequest;
import com.dendi.peony.api.model.RefreshRequest;
import com.dendi.peony.api.model.RoomResponse;
import com.dendi.peony.api.model.SigninRequest;
import com.dendi.peony.api.model.SignupRequest;
import com.dendi.peony.api.model.Species;
import com.dendi.peony.api.model.UserPlantResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Contract tests for the DTOs generated from {@code openapi/peony-api.yaml}.
 *
 * <p>The spec is the single source of truth for the v1 API contract (issue #13): the recognition
 * shapes must match the merged endpoint exactly, including the tier and capability fields from #12
 * and the free-tier omission behavior (premium-only fields absent from the JSON, not null). The
 * plants, locations, and auth schemas are pinned here so #10 builds to the same contract.
 */
class OpenApiModelsTest {

  private static final ObjectMapper JSON =
      new ObjectMapper()
          .registerModule(new JavaTimeModule())
          .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

  /** A PlantNet v2 identify response, using the upstream "results" key. */
  private static final String PLANTNET_BODY =
      """
      {
        "successful": true,
        "results": [
          {
            "score": 0.9,
            "species": {
              "scientificName": "Ajuga reptans L.",
              "scientificNameWithoutAuthor": "Ajuga reptans",
              "scientificNameAuthorship": "L.",
              "genus": {"scientificName": "Ajuga"},
              "family": {"scientificName": "Lamiaceae"},
              "commonNames": ["Carpet bugle"]
            },
            "gbif": {"id": "2868390"},
            "powo": {"id": "30010088-2"}
          }
        ],
        "api_version": "2025-01-17",
        "remaining_identification_requests": 42
      }
      """;

  /** Parses the upstream PlantNet response, whose predictions live under "results". */
  @Test
  void testPlantRecognitionResult_ParsesPlantNetResponse_WhenResultsKeyUsed() throws IOException {
    PlantRecognitionResult result = JSON.readValue(PLANTNET_BODY, PlantRecognitionResult.class);

    assertTrue(result.getSuccessful());
    assertEquals("2025-01-17", result.getApiVersion());
    assertEquals(42, result.getRemainingIdentificationRequests());
    assertEquals(1, result.getHits().size());

    HitResponse hit = result.getHits().get(0);
    assertEquals(0.9, hit.getScore());
    assertEquals("Ajuga reptans L.", hit.getSpecies().getScientificName());
    assertEquals("2868390", hit.getGbif().getId());
    assertEquals("30010088-2", hit.getPowo().getId());
  }

  /** The free-tier shape omits premium-only fields from the JSON entirely. */
  @Test
  void testPlantRecognitionResult_OmitsPremiumFields_WhenFreeTierShapeSerialized()
      throws IOException {
    PlantRecognitionResult basic = freeTierShape();

    String json = JSON.writeValueAsString(basic);

    assertFalse(json.contains("\"gbif\""), json);
    assertFalse(json.contains("\"powo\""), json);
    assertFalse(json.contains("\"genus\""), json);
    assertFalse(json.contains("\"family\""), json);
    assertFalse(json.contains("\"scientificNameWithoutAuthor\""), json);
    assertFalse(json.contains("\"remaining_identification_requests\""), json);
    assertTrue(json.contains("\"tier\":\"free\""), json);
    assertTrue(json.contains("RECOGNITION_BASIC"), json);
  }

  /** The premium-tier shape carries the complete PlantNet result plus tier flags. */
  @Test
  void testPlantRecognitionResult_RoundTripsCompleteResult_WhenPremiumTierShape()
      throws IOException {
    PlantRecognitionResult full = JSON.readValue(PLANTNET_BODY, PlantRecognitionResult.class);
    full.setTier(PlantRecognitionResult.TierEnum.PREMIUM);
    full.setCapabilities(
        List.of(
            PlantRecognitionResult.CapabilitiesEnum.PLANTS_READ,
            PlantRecognitionResult.CapabilitiesEnum.PLANTS_CREATE,
            PlantRecognitionResult.CapabilitiesEnum.PLANTS_UPDATE_NOTES,
            PlantRecognitionResult.CapabilitiesEnum.RECOGNITION_COMPLETE,
            PlantRecognitionResult.CapabilitiesEnum.MULTI_DEVICE_SYNC,
            PlantRecognitionResult.CapabilitiesEnum.ADVANCED_ANALYTICS));

    PlantRecognitionResult roundTrip =
        JSON.readValue(JSON.writeValueAsString(full), PlantRecognitionResult.class);

    assertEquals(PlantRecognitionResult.TierEnum.PREMIUM, roundTrip.getTier());
    assertEquals(6, roundTrip.getCapabilities().size());
    assertEquals(42, roundTrip.getRemainingIdentificationRequests());
    HitResponse hit = roundTrip.getHits().get(0);
    assertNotNull(hit.getSpecies().getGenus());
    assertNotNull(hit.getSpecies().getFamily());
    assertEquals("2868390", hit.getGbif().getId());
    assertEquals("30010088-2", hit.getPowo().getId());
  }

  /** The recognition request bundles the image payload with the upload options. */
  @Test
  void testRecognitionRequest_CarriesImagePayload_WhenSingleImageProvided() {
    byte[] image = new byte[] {(byte) 0xFF, (byte) 0xD8};

    RecognitionRequest request = new RecognitionRequest();
    request.setImage(image);
    request.setFormat("image/jpeg");

    assertArrayEquals(image, request.getImage());
    assertEquals("image/jpeg", request.getFormat());
    // The generator initializes list fields to an empty list (not null); the client treats both
    // the same and falls back to auto-detection
    assertTrue(request.getOrgans().isEmpty());
    assertNull(request.getLanguage());
  }

  /** The plant schema exposes the tier-contract field groups (free and premium). */
  @Test
  void testPlant_ExposesTierContractFields_WhenGeneratedFromSpec() {
    PlantResponse plant = new PlantResponse();
    plant.setId(7L);
    // Free-tier field set: identity + tags + basic light/water/toxicity
    plant.setCommonName("Ajuga reptans");
    plant.setScientificName("Ajuga reptans");
    plant.setFamily("Lamiaceae");
    plant.setTags(List.of("groundcover", "pet-friendly"));
    plant.setLightNeeds("partial shade");
    plant.setWaterFrequency(7);
    plant.setToPets(false);
    plant.setToHumans(false);
    // Premium-only field set: full care profile + growth
    plant.setHumidity("moderate");
    plant.setTempRange("10-25C");
    plant.setDifficulty("easy");
    plant.setNativeRegion("Europe");
    plant.setMatureSize("10cm tall");

    assertEquals(7L, plant.getId());
    assertEquals("Ajuga reptans", plant.getCommonName());
    assertEquals("Lamiaceae", plant.getFamily());
    assertEquals(List.of("groundcover", "pet-friendly"), plant.getTags());
    assertEquals("partial shade", plant.getLightNeeds());
    assertEquals(7, plant.getWaterFrequency());
    assertEquals(false, plant.getToPets());
    assertEquals(false, plant.getToHumans());
    assertEquals("moderate", plant.getHumidity());
    assertEquals("10-25C", plant.getTempRange());
    assertEquals("easy", plant.getDifficulty());
    assertEquals("Europe", plant.getNativeRegion());
    assertEquals("10cm tall", plant.getMatureSize());
  }

  /**
   * The user plant schema composes the plant response with the caller's per-user record: nickname
   * and watering dates are always present, locations carry the caller-owned location shape, and
   * the plant fields come from the shared PlantResponse shape (free-tier omission still applies).
   */
  @Test
  void testUserPlantResponse_CarriesUserStateAndPlantFields_WhenGeneratedFromSpec()
      throws IOException {
    PlantLocationResponse location = new PlantLocationResponse();
    location.setId(11L);
    location.setRoom(new RoomResponse().id(2L).name("Kitchen").houseId(1L));
    location.setHouse(new HouseResponse().id(1L).name("Home"));

    UserPlantResponse row = new UserPlantResponse();
    row.setId(7L);
    row.setCommonName("Monstera");
    row.setScientificName("Monstera deliciosa");
    row.setWaterFrequency(7);
    row.setNickname("Living room monstera");
    row.setLastWateredAt(LocalDateTime.of(2026, 10, 30, 9, 15));
    row.setNextWaterDueAt(LocalDateTime.of(2026, 11, 6, 9, 15));
    row.setLocations(List.of(location));

    // The user plant composes the shared plant response fields (allOf in the spec).
    assertEquals(7L, row.getId());
    assertEquals("Monstera", row.getCommonName());
    assertEquals(7, row.getWaterFrequency());
    assertEquals("Living room monstera", row.getNickname());
    assertEquals(11L, row.getLocations().get(0).getId());

    String json = JSON.writeValueAsString(row);
    assertTrue(json.contains("\"commonName\":\"Monstera\""), json);
    assertTrue(json.contains("\"nickname\":\"Living room monstera\""), json);
    assertTrue(json.contains("\"lastWateredAt\":\"2026-10-30T09:15:00\""), json);
    assertTrue(json.contains("\"nextWaterDueAt\":\"2026-11-06T09:15:00\""), json);
    assertTrue(json.contains("\"locations\":["), json);
    // A free-tier shaped row leaves the premium fields null; they must be omitted, not null.
    assertFalse(json.contains("\"humidity\""), json);
    assertFalse(json.contains("\"tempRange\""), json);
    assertFalse(json.contains("\"difficulty\""), json);
    assertFalse(json.contains("\"nativeRegion\""), json);
    assertFalse(json.contains("\"matureSize\""), json);
  }

  /** The location schemas model the nested house -> room hierarchy. */
  @Test
  void testPlantLocation_MatchesHouseRoomNesting_WhenGeneratedFromSpec() {
    HouseResponse house = new HouseResponse();
    house.setId(1L);
    house.setName("Apartment");

    RoomResponse room = new RoomResponse();
    room.setId(2L);
    room.setName("Balcony");
    room.setHouseId(1L);

    PlantLocationResponse location = new PlantLocationResponse();
    location.setId(3L);
    location.setHouse(house);
    location.setRoom(room);

    assertEquals("Apartment", location.getHouse().getName());
    assertEquals("Balcony", location.getRoom().getName());
    assertEquals(1L, location.getRoom().getHouseId());
  }

  /** The auth schemas match the tiered-access design of #12. */
  @Test
  void testAuthModels_MatchTieredAccessDesign_WhenGeneratedFromSpec() {
    SignupRequest signup = new SignupRequest();
    signup.setUsername("ada");
    signup.setEmail("ada@example.com");
    signup.setPassword("supersecret1");
    signup.setFirstName("Ada");
    signup.setLastName("Lovelace");
    assertEquals("ada", signup.getUsername());
    assertEquals("supersecret1", signup.getPassword());

    ProfileResponse profile = new ProfileResponse();
    profile.setId(9L);
    profile.setUsername("ada");
    profile.setEmail("ada@example.com");
    profile.setFirstName("Ada");
    profile.setLastName("Lovelace");
    profile.setTier(ProfileResponse.TierEnum.PREMIUM);
    profile.setPhotoUploadsPerMonth(null);
    profile.setCapabilities(List.of(ProfileResponse.CapabilitiesEnum.RECOGNITION_COMPLETE));
    assertEquals(ProfileResponse.TierEnum.PREMIUM, profile.getTier());
    assertNull(profile.getPhotoUploadsPerMonth());

    SigninRequest signin = new SigninRequest();
    signin.setUsername("ada");
    signin.setPassword("supersecret1");
    assertEquals("ada", signin.getUsername());

    RefreshRequest refresh = new RefreshRequest();
    refresh.setRefreshToken("opaque-refresh-token");
    assertEquals("opaque-refresh-token", refresh.getRefreshToken());

    AuthResponse auth = new AuthResponse();
    auth.setToken("access-token");
    auth.setRefreshToken("opaque-refresh-token");
    auth.setProfile(profile);
    assertEquals("access-token", auth.getToken());
    assertEquals("ada", auth.getProfile().getUsername());
  }

  /** The species schema carries the taxonomic wrappers of the PlantNet response. */
  @Test
  void testSpecies_CarriesTaxonomicWrappers_WhenGeneratedFromSpec() {
    Species species = new Species();
    species.setScientificName("Ajuga reptans L.");
    species.setScientificNameWithoutAuthor("Ajuga reptans");
    species.setScientificNameAuthorship("L.");
    species.setCommonNames(List.of("Carpet bugle"));

    Genus genus = new Genus();
    genus.setScientificName("Ajuga");
    species.setGenus(genus);

    assertEquals("Ajuga", species.getGenus().getScientificName());
    assertEquals(List.of("Carpet bugle"), species.getCommonNames());
  }

  /** Builds the free-tier basic shape: top hit only, reduced species detail. */
  private static PlantRecognitionResult freeTierShape() {
    Species species = new Species();
    species.setScientificName("Ajuga reptans L.");
    species.setCommonNames(List.of("Carpet bugle"));

    HitResponse hit = new HitResponse();
    hit.setScore(0.9);
    hit.setSpecies(species);

    PlantRecognitionResult basic = new PlantRecognitionResult();
    basic.setSuccessful(true);
    basic.setApiVersion("2025-01-17");
    basic.setHits(List.of(hit));
    basic.setTier(PlantRecognitionResult.TierEnum.FREE);
    basic.setCapabilities(
        List.of(
            PlantRecognitionResult.CapabilitiesEnum.PLANTS_READ,
            PlantRecognitionResult.CapabilitiesEnum.PLANTS_CREATE,
            PlantRecognitionResult.CapabilitiesEnum.PLANTS_UPDATE_NOTES,
            PlantRecognitionResult.CapabilitiesEnum.RECOGNITION_BASIC));
    return basic;
  }
}
