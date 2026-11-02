package com.dendi.peony.service.plant;

import com.dendi.peony.api.model.CreatePlantFromScanRequest;
import com.dendi.peony.api.model.CreatePlantRequest;
import com.dendi.peony.api.model.PlantResponse;
import com.dendi.peony.api.model.UserPlantResponse;
import com.dendi.peony.entity.Plant;
import com.dendi.peony.entity.PlantSource;
import com.dendi.peony.entity.UserTier;
import com.dendi.peony.exception.PlantNotFoundException;
import com.dendi.peony.repository.PlantRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Plant catalog operations for the v1 plants endpoints (issue #10 Phase 1).
 *
 * <p>Responses honor the tier field visibility from docs/api-tier-contracts.md: free-tier responses
 * omit the premium-only fields (null fields are absent from the JSON through the generated models'
 * NON_NULL serialization), premium-tier responses carry the full plant object. Both registration
 * paths always create one individual for the caller; scan registration additionally creates a
 * catalogue row with identity and taxonomy only when the species is new.
 */
@Service
@RequiredArgsConstructor
public class PlantService {

  private final PlantRepository plantRepository;
  private final UserPlantService userPlantService;
  private final Clock clock;

  /**
   * Typahead search over names, scientific names, and tags.
   *
   * @param q query text; blank or null returns the whole catalog
   * @param tier tier of the calling account
   * @return tier-filtered matches ordered by name
   */
  @Transactional(readOnly = true)
  public List<PlantResponse> search(String q, UserTier tier) {
    String query = q == null || q.isBlank() ? null : q.trim();
    return plantRepository.searchPlants(query).stream()
        .map(plant -> PlantResponseMapper.toModel(plant, tier))
        .toList();
  }

  /**
   * Manually registers a plant and always creates a new individual for the caller.
   *
   * <p>The catalog is keyed by scientific name: an existing entry is reused so the catalog stays
   * the single source of truth, while each registration still creates its own individual. The
   * returned identity is the individual, not the catalog plant.
   *
   * @param request registration payload
   * @param userId id of the registering account
   * @param tier tier of the registering account (shapes the response)
   * @return the created individual
   * @throws IllegalArgumentException when a required field is missing
   */
  @Transactional
  public UserPlantResponse register(CreatePlantRequest request, Long userId, UserTier tier) {
    RequiredText.requireText(request.getScientificName(), "scientificName");
    String scientificName = request.getScientificName().trim();
    Plant plant =
        plantRepository
            .findByScientificName(scientificName)
            .orElseGet(() -> saveCatalogEntry(request, scientificName));
    return userPlantService.addRegisteredIndividual(plant, userId, tier);
  }

  /**
   * Registers a plant identified by a scan and always creates a new individual for the caller.
   *
   * <p>The catalog entry is upserted by scientific name in one statement, so concurrent scans of
   * the same species cannot surface a unique-constraint failure. A newly created row records
   * identity and taxonomy only with {@code source = PLANTNET_SCAN}; an existing row keeps its care
   * fields and source, with only blank common-name/family values filled. The catalog entry and the
   * individual are written in one transaction.
   *
   * @param request scan registration payload
   * @param userId id of the registering account
   * @param tier tier of the registering account (shapes the response)
   * @return the created individual
   * @throws IllegalArgumentException when scientificName is missing/blank, the nickname is too
   *     long, or lastWateredAt is malformed
   * @throws com.dendi.peony.exception.RoomNotFoundException when the room is missing or outside the
   *     caller's houses
   */
  @Transactional
  public UserPlantResponse registerFromScan(
      CreatePlantFromScanRequest request, Long userId, UserTier tier) {
    RequiredText.requireText(request.getScientificName(), "scientificName");
    String scientificName = request.getScientificName().trim();
    plantRepository.upsertScanCatalogueEntry(
        scientificName,
        trimToNull(request.getCommonName()),
        trimToNull(request.getFamily()),
        PlantSource.PLANTNET_SCAN.name(),
        LocalDateTime.now(clock));
    Plant plant =
        plantRepository
            .findByScientificName(scientificName)
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Scan catalogue upsert produced no plant for " + scientificName));
    return userPlantService.createIndividual(
        plant,
        userId,
        tier,
        request.getNickname(),
        request.getRoomId(),
        request.getLastWateredAt());
  }

  /**
   * Loads a plant for the detail endpoint.
   *
   * @param plantId catalog plant id
   * @param tier tier of the calling account
   * @return the tier-filtered plant
   * @throws PlantNotFoundException when no plant exists with the id
   */
  @Transactional(readOnly = true)
  public PlantResponse get(Long plantId, UserTier tier) {
    return PlantResponseMapper.toModel(findEntity(plantId), tier);
  }

  /** Persists a new catalog entry from the registration payload. */
  private Plant saveCatalogEntry(CreatePlantRequest request, String scientificName) {
    Plant plant = new Plant();
    plant.setName(trimToNull(request.getCommonName()));
    plant.setScientificName(scientificName);
    // description is not part of the API contract, but the column is NOT NULL
    plant.setDescription("");
    plant.setWateringFrequency(request.getWaterFrequency());
    plant.setFamily(trimToNull(request.getFamily()));
    plant.setSource(PlantSource.MANUAL);
    plant.setTags(request.getTags() == null ? null : request.getTags().toArray(new String[0]));
    plant.setLightNeeds(request.getLightNeeds());
    plant.setHumidity(request.getHumidity());
    plant.setTempRange(request.getTempRange());
    plant.setToPets(request.getToPets());
    plant.setToHumans(request.getToHumans());
    plant.setDifficulty(request.getDifficulty());
    plant.setNativeRegion(request.getNativeRegion());
    plant.setMatureSize(request.getMatureSize());
    plant.setCreatedAt(LocalDateTime.now(clock));
    return plantRepository.save(plant);
  }

  private Plant findEntity(Long plantId) {
    return plantRepository.findById(plantId).orElseThrow(() -> new PlantNotFoundException(plantId));
  }

  private static String trimToNull(String value) {
    if (value == null) {
      return null;
    }
    String trimmed = value.trim();
    return trimmed.isEmpty() ? null : trimmed;
  }
}
